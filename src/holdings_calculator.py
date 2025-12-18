"""持仓计算模块 - 支持扣款与份额确认分离

设计理念：
- 扣款（buy_debit）：钱已从余利宝/稳利宝扣除，但份额未到账
- 份额确认（buy_confirm）：份额正式进入持仓
- 兼容旧数据：buy 视为"当天既扣款又确认"

支持的交易类型 (action):
  - buy_debit: 扣款事件（钱已扣，份额未到）
  - buy_confirm: 份额确认事件（份额正式进入持仓）
  - buy: 兼容旧数据，当天既扣款又确认
  - sell: 卖出，份额减少，成本按比例减少
  - dividend: 分红，份额增加，成本不变

CSV 格式 (data/transactions.csv):
  date,product_code,action,amount,shares,fee,nav,nav_date,order_id,note
  
  - buy_debit: date, product_code, action=buy_debit, amount, fee, order_id(建议必填)
  - buy_confirm: date, product_code, action=buy_confirm, shares, nav, nav_date, order_id(必须匹配debit)
  - buy: 所有字段都需要（兼容旧数据）
  - sell: date, product_code, action, shares, nav, fee（可选）
  - dividend: date, product_code, action, shares
"""
import csv
import json
from pathlib import Path
from decimal import Decimal, ROUND_HALF_UP
from datetime import datetime
import logging
from typing import Tuple, Optional, List, Dict

logger = logging.getLogger(__name__)


def safe_decimal(value, default=Decimal('0')) -> Decimal:
    """
    安全地将值转换为Decimal
    支持：None、空字符串、'-'、带逗号的数字、普通数字
    """
    if value is None:
        return default
    if isinstance(value, Decimal):
        return value
    if isinstance(value, (int, float)):
        return Decimal(str(value))
    
    s = str(value).strip()
    if s == '' or s == '-':
        return default
    
    # 移除千分位逗号
    s = s.replace(',', '')
    
    try:
        return Decimal(s)
    except Exception as e:
        logger.warning(f"Decimal 解析失败: '{value}' -> 使用默认值 {default}")
        return default


def load_transactions(transactions_path: Path) -> List[Dict]:
    """
    加载交易流水
    :param transactions_path: 交易流水文件路径
    :return: 交易记录列表
    """
    if not transactions_path.exists():
        return []
    
    transactions = []
    with open(transactions_path, 'r', encoding='utf-8-sig') as f:
        reader = csv.DictReader(f)
        for row in reader:
            # 跳过空行或缺少必要字段的行
            if row.get('date') and row.get('product_code'):
                transactions.append(row)
    
    return transactions


def load_base_holdings(holdings_path: Path = None) -> Dict[str, Decimal]:
    """
    加载基础持仓（holdings.json）
    :param holdings_path: holdings.json 文件路径
    :return: {product_code: base_shares}
    """
    from config_loader import get_project_root
    
    if holdings_path is None:
        holdings_path = get_project_root() / "config" / "holdings.json"
    
    if not holdings_path.exists():
        return {}
    
    base_holdings = {}
    try:
        with open(holdings_path, 'r', encoding='utf-8') as f:
            holdings = json.load(f)
            for item in holdings:
                product_id = item.get('product_code', '')
                amount = safe_decimal(item.get('amount', 0))
                if product_id:
                    base_holdings[product_id] = amount
    except Exception as e:
        logger.warning(f"加载 holdings.json 失败: {e}")
    
    return base_holdings


class HoldingsCalculator:
    """
    持仓计算器 - 支持扣款与份额确认分离
    
    核心概念：
    - shares: 已确认份额（只有 buy_confirm/buy/dividend 才增加）
    - cost: 持仓成本（平均成本法，卖出按比例减少）
    - cash_in_transit: 在途资金（buy_debit 增加，buy_confirm 减少）
    - principal_total: 累计投入本金（buy_debit/buy 增加，sell 回笼不减少）
    """
    
    def __init__(self, transactions_path: Path = None, holdings_path: Path = None):
        from config_loader import get_project_root
        
        if transactions_path is None:
            transactions_path = get_project_root() / "data" / "transactions.csv"
        if holdings_path is None:
            holdings_path = get_project_root() / "config" / "holdings.json"
        
        self.transactions_path = transactions_path
        self.holdings_path = holdings_path
        
        # 加载数据
        self.transactions = load_transactions(transactions_path)
        self.base_holdings = load_base_holdings(holdings_path)
        
        # 按 order_id 索引 debit 记录（用于 confirm 匹配）
        self._debit_index: Dict[str, Dict] = {}
    
    def _build_debit_index(self, product_code: str, asof_date: str):
        """
        构建扣款记录索引（按 order_id）
        """
        self._debit_index = {}
        for t in self.transactions:
            if t['product_code'] != product_code:
                continue
            if t['date'] > asof_date:
                continue
            
            action = t.get('action', '').upper()
            order_id = t.get('order_id', '').strip()
            
            if action == 'BUY_DEBIT' and order_id:
                amount = safe_decimal(t.get('amount', 0))
                fee = safe_decimal(t.get('fee', 0))
                net_amount = amount - fee  # 净申购金额（可用于买入份额的金额）
                
                self._debit_index[order_id] = {
                    'date': t['date'],
                    'amount': amount,
                    'fee': fee,
                    'net_amount': net_amount,
                    'confirmed': False
                }
    
    def get_holdings_as_of(self, asof_date: str) -> Dict[str, Dict[str, Decimal]]:
        """
        获取截至某日期的所有产品已确认持仓
        
        :param asof_date: 截止日期 (YYYY-MM-DD)
        :return: {product_code: {"shares": Decimal, "cost": Decimal}}
        """
        # 获取所有有交易的产品
        product_codes = set()
        for t in self.transactions:
            if t['date'] <= asof_date:
                product_codes.add(t['product_code'])
        
        # 加上有基础持仓的产品
        for code in self.base_holdings:
            product_codes.add(code)
        
        result = {}
        for product_code in product_codes:
            shares, cost = self._calc_position_for_product(product_code, asof_date)
            result[product_code] = {"shares": shares, "cost": cost}
        
        return result
    
    def get_cash_in_transit_as_of(self, asof_date: str) -> Dict[str, Decimal]:
        """
        获取截至某日期的所有产品在途资金
        
        :param asof_date: 截止日期 (YYYY-MM-DD)
        :return: {product_code: Decimal} 每个产品的在途资金合计
        """
        # 获取所有有交易的产品
        product_codes = set(t['product_code'] for t in self.transactions if t['date'] <= asof_date)
        
        result = {}
        for product_code in product_codes:
            cash = self._calc_cash_in_transit(product_code, asof_date)
            if cash > 0:
                result[product_code] = cash
        
        return result
    
    def get_principal_total_as_of(self, asof_date: str) -> Dict[str, Decimal]:
        """
        获取截至某日期的所有产品累计投入本金
        
        :param asof_date: 截止日期 (YYYY-MM-DD)
        :return: {product_code: Decimal} 每个产品的累计投入本金
        """
        product_codes = set(t['product_code'] for t in self.transactions if t['date'] <= asof_date)
        
        result = {}
        for product_code in product_codes:
            principal = self._calc_principal_total(product_code, asof_date)
            result[product_code] = principal
        
        return result
    
    def _calc_position_for_product(self, product_code: str, asof_date: str) -> Tuple[Decimal, Decimal]:
        """
        计算单个产品的已确认持仓（份额和成本）
        
        返回: (shares, cost)
        """
        # 基础份额
        base_shares = self.base_holdings.get(product_code, Decimal('0'))
        
        # 筛选该产品、截止日期前的交易
        product_transactions = [
            t for t in self.transactions
            if t['product_code'] == product_code and t['date'] <= asof_date
        ]
        product_transactions.sort(key=lambda x: x['date'])
        
        # 构建 debit 索引
        self._build_debit_index(product_code, asof_date)
        
        shares = base_shares
        cost = Decimal('0')
        
        for t in product_transactions:
            action = t.get('action', '').upper()
            order_id = t.get('order_id', '').strip()
            
            if action == 'BUY_DEBIT':
                # 扣款事件：不改变 shares/cost，只记录到 debit_index（已在 _build_debit_index 处理）
                pass
            
            elif action == 'BUY_CONFIRM':
                # 份额确认事件
                confirmed_shares = safe_decimal(t.get('shares', 0))
                
                if confirmed_shares <= 0:
                    logger.warning(f"buy_confirm 缺少有效份额: {t}")
                    continue
                
                # 查找匹配的 debit
                if order_id and order_id in self._debit_index:
                    debit = self._debit_index[order_id]
                    net_amount = debit['net_amount']
                    self._debit_index[order_id]['confirmed'] = True
                else:
                    # 没有匹配的 debit，尝试降级处理
                    amount = safe_decimal(t.get('amount', 0))
                    fee = safe_decimal(t.get('fee', 0))
                    if amount > 0:
                        # 有 amount 字段，可以降级
                        net_amount = amount - fee
                        logger.info(f"buy_confirm 降级处理（无匹配 debit）: order_id={order_id}, amount={amount}")
                    else:
                        # 无法计算成本，报错但不崩溃
                        logger.error(f"buy_confirm 无法计算成本（缺少 debit 或 amount）: {t}")
                        net_amount = Decimal('0')
                
                shares += confirmed_shares
                cost += net_amount
            
            elif action == 'BUY':
                # 兼容旧数据：当天既扣款又确认
                trans_shares = safe_decimal(t.get('shares', 0))
                amount = safe_decimal(t.get('amount', 0))
                fee = safe_decimal(t.get('fee', 0))
                
                if trans_shares > 0:
                    shares += trans_shares
                    cost += amount  # 注意：旧逻辑是 amount + fee，但通常 amount 已是总成本
                else:
                    # 某些产品（如货基）用 amount 作为份额
                    shares += amount
                    cost += amount
            
            elif action == 'SELL':
                # 卖出：份额减少，成本按比例减少
                sell_shares = safe_decimal(t.get('shares', 0))
                if sell_shares > 0 and shares > 0:
                    cost_reduction = cost * sell_shares / shares
                    cost -= cost_reduction
                    shares -= sell_shares
            
            elif action == 'DIVIDEND':
                # 分红：份额增加，成本不变
                div_shares = safe_decimal(t.get('shares', 0))
                if div_shares > 0:
                    shares += div_shares
        
        # 确保不为负
        if shares < 0:
            logger.warning(f"产品 {product_code} 计算出负份额 {shares}，设为0")
            shares = Decimal('0')
        if cost < 0:
            cost = Decimal('0')
        
        return shares, cost
    
    def _calc_cash_in_transit(self, product_code: str, asof_date: str) -> Decimal:
        """
        计算单个产品的在途资金（已扣款但未确认的净额合计）
        """
        product_transactions = [
            t for t in self.transactions
            if t['product_code'] == product_code and t['date'] <= asof_date
        ]
        product_transactions.sort(key=lambda x: x['date'])
        
        # 收集所有 debit
        debit_pool: Dict[str, Decimal] = {}  # order_id -> net_amount
        
        for t in product_transactions:
            action = t.get('action', '').upper()
            order_id = t.get('order_id', '').strip()
            
            if action == 'BUY_DEBIT':
                amount = safe_decimal(t.get('amount', 0))
                fee = safe_decimal(t.get('fee', 0))
                net_amount = amount - fee
                
                if order_id:
                    debit_pool[order_id] = net_amount
                else:
                    # 无 order_id 的 debit，用日期+金额作为临时 key
                    temp_key = f"_auto_{t['date']}_{amount}"
                    debit_pool[temp_key] = net_amount
            
            elif action == 'BUY_CONFIRM':
                if order_id and order_id in debit_pool:
                    del debit_pool[order_id]
            
            # 注意：兼容旧 buy 不产生在途，因为它是"当天确认"
        
        # 在途资金 = 所有未确认 debit 的净额合计
        return sum(debit_pool.values(), Decimal('0'))
    
    def _calc_principal_total(self, product_code: str, asof_date: str) -> Decimal:
        """
        计算单个产品的累计投入本金
        
        规则：
        - buy_debit: principal += amount（扣款时计入）
        - buy: principal += amount（兼容旧数据）
        - sell: 不减少（卖出回笼不影响累计投入）
        - buy_confirm: 不增加（只是确认，钱已在 debit 时计入）
        """
        product_transactions = [
            t for t in self.transactions
            if t['product_code'] == product_code and t['date'] <= asof_date
        ]
        
        principal = Decimal('0')
        
        for t in product_transactions:
            action = t.get('action', '').upper()
            amount = safe_decimal(t.get('amount', 0))
            
            if action == 'BUY_DEBIT':
                # 扣款时计入本金（用 amount，即总扣款额）
                principal += amount
            
            elif action == 'BUY':
                # 兼容旧数据
                principal += amount
            
            # buy_confirm, sell, dividend 都不影响 principal_total
        
        return principal


# ============ 兼容旧 API ============

def calc_position_incremental(
    product_code: str,
    asof_date: str,
    transactions_path: Path = None,
    holdings_path: Path = None
) -> Tuple[Decimal, Decimal]:
    """
    增量模式计算持仓（兼容旧 API）
    
    :return: (shares_total, cost_total)
    """
    calc = HoldingsCalculator(transactions_path, holdings_path)
    holdings = calc.get_holdings_as_of(asof_date)
    
    if product_code in holdings:
        return holdings[product_code]["shares"], holdings[product_code]["cost"]
    
    # 检查基础持仓
    base_shares = calc.base_holdings.get(product_code, Decimal('0'))
    return base_shares, Decimal('0')


def has_transactions(product_code: str, transactions_path: Path = None) -> bool:
    """
    检查某产品是否有交易流水
    """
    from config_loader import get_project_root
    
    if transactions_path is None:
        transactions_path = get_project_root() / "data" / "transactions.csv"
    
    all_transactions = load_transactions(transactions_path)
    return any(t['product_code'] == product_code for t in all_transactions)


def get_all_product_positions(asof_date: str, transactions_path: Path = None) -> Dict[str, Tuple[Decimal, Decimal]]:
    """
    获取所有有交易记录的产品的持仓（兼容旧 API）
    :return: {product_code: (shares, cost)}
    """
    calc = HoldingsCalculator(transactions_path)
    holdings = calc.get_holdings_as_of(asof_date)
    
    return {code: (h["shares"], h["cost"]) for code, h in holdings.items()}


# ============ 便捷函数 ============

def get_holdings_calculator(transactions_path: Path = None, holdings_path: Path = None) -> HoldingsCalculator:
    """
    获取持仓计算器实例
    """
    return HoldingsCalculator(transactions_path, holdings_path)


if __name__ == "__main__":
    # 简单测试
    from config_loader import get_project_root
    
    calc = HoldingsCalculator()
    asof = datetime.now().strftime('%Y-%m-%d')
    
    print(f"截至 {asof} 的持仓:")
    holdings = calc.get_holdings_as_of(asof)
    for code, data in holdings.items():
        print(f"  {code}: 份额={data['shares']:.2f}, 成本={data['cost']:.2f}")
    
    print(f"\n在途资金:")
    cash_in_transit = calc.get_cash_in_transit_as_of(asof)
    for code, cash in cash_in_transit.items():
        print(f"  {code}: {cash:.2f}")
    
    print(f"\n累计投入本金:")
    principal = calc.get_principal_total_as_of(asof)
    for code, p in principal.items():
        print(f"  {code}: {p:.2f}")
