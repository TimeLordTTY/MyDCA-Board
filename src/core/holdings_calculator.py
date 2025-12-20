"""持仓计算模块 - 支持扣款与份额确认分离

设计理念：
- 扣款（buy_debit）：钱已从余利宝/稳利宝扣除，但份额未到账
- 份额确认（buy_confirm）：份额正式进入持仓
- 兼容旧数据：buy 视为"当天既扣款又确认"（复用 buy_debit + buy_confirm 逻辑）

支持的交易类型 (action):
  - buy_debit: 扣款事件（钱已扣，份额未到）
  - buy_confirm: 份额确认事件（份额正式进入持仓）
  - buy: 兼容旧数据，当天既扣款又确认
  - sell: 卖出，份额减少，成本按比例减少
  - sell_confirm: 赎回确认，份额减少，成本按比例减少
  - dividend: 分红，份额增加，成本不变

CSV 格式 (data/transactions.csv):
  date,product_code,action,amount,shares,fee,nav,nav_date,order_id,note
  
口径说明：
  - cost: 净申购额累计（amount - fee），卖出时按比例结转减少
  - principal_total: 真实扣款本金累计（buy_debit/buy 的 amount），卖出不减少
  - cash_in_transit: 在途资金（已扣款未确认的净额）
  - total_redemption: 累计赎回到账净额（sell/sell_confirm 的 amount）
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


def normalize_action(action: str) -> str:
    """
    标准化 action 字段：strip + 小写
    """
    return action.strip().lower() if action else ''


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
    from data.config_loader import get_project_root
    
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
    - cost: 持仓成本（净申购额口径，卖出按比例减少）
    - cash_in_transit: 在途资金（buy_debit 增加，buy_confirm 减少）
    - principal_total: 累计投入本金（buy_debit/buy 增加，卖出不减少）
    - total_redemption: 累计赎回到账净额
    """
    
    def __init__(self, transactions_path: Path = None, holdings_path: Path = None):
        from data.config_loader import get_project_root
        
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
            
            action = normalize_action(t.get('action', ''))
            order_id = t.get('order_id', '').strip()
            
            if action == 'buy_debit' and order_id:
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
    
    def get_total_redemption_as_of(self, asof_date: str) -> Dict[str, Decimal]:
        """
        获取截至某日期的所有产品累计赎回金额
        
        :param asof_date: 截止日期 (YYYY-MM-DD)
        :return: {product_code: total_redemption}
        """
        product_codes = set(t['product_code'] for t in self.transactions if t['date'] <= asof_date)
        
        result = {}
        for product_code in product_codes:
            redemption = self._calc_total_redemption(product_code, asof_date)
            result[product_code] = redemption
        
        return result
    
    def get_all_holdings_data_as_of(self, asof_date: str) -> Dict[str, Dict]:
        """
        获取截至某日期的所有产品完整持仓数据
        
        :param asof_date: 截止日期 (YYYY-MM-DD)
        :return: {product_code: {
            "shares": Decimal,
            "cost": Decimal,
            "cash_in_transit": Decimal,
            "principal_total": Decimal,
            "total_redemption": Decimal
        }}
        """
        # 获取所有相关产品
        product_codes = set()
        for t in self.transactions:
            if t['date'] <= asof_date:
                product_codes.add(t['product_code'])
        for code in self.base_holdings:
            product_codes.add(code)
        
        result = {}
        for product_code in product_codes:
            shares, cost = self._calc_position_for_product(product_code, asof_date)
            cash_in_transit = self._calc_cash_in_transit(product_code, asof_date)
            principal_total = self._calc_principal_total(product_code, asof_date)
            total_redemption = self._calc_total_redemption(product_code, asof_date)
            
            result[product_code] = {
                "shares": shares,
                "cost": cost,
                "cash_in_transit": cash_in_transit,
                "principal_total": principal_total,
                "total_redemption": total_redemption
            }
        
        return result
    
    def _calc_position_for_product(self, product_code: str, asof_date: str) -> Tuple[Decimal, Decimal]:
        """
        计算单个产品的已确认持仓（份额和成本）
        
        返回: (shares, cost)
        
        buy 兼容模式说明：
        buy 等价于同日 buy_debit + buy_confirm 的原子组合，
        复用同一套计算逻辑，不写两套分叉。
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
            action = normalize_action(t.get('action', ''))
            order_id = t.get('order_id', '').strip()
            
            if action == 'buy_debit':
                # 扣款事件：不改变 shares/cost，只记录到 debit_index（已在 _build_debit_index 处理）
                pass
            
            elif action == 'buy_confirm':
                # 份额确认事件
                confirmed_shares = safe_decimal(t.get('shares', 0))
                
                if confirmed_shares <= 0:
                    logger.warning(f"buy_confirm 缺少有效份额: {t}")
                    continue
                
                if not order_id:
                    logger.error(f"buy_confirm 必须提供 order_id: {t}")
                    continue
                
                # 查找匹配的 debit
                if order_id in self._debit_index:
                    debit = self._debit_index[order_id]
                    net_amount = debit['net_amount']
                    self._debit_index[order_id]['confirmed'] = True
                else:
                    # 降级路径：未找到匹配的 debit
                    amount = safe_decimal(t.get('amount', 0))
                    fee = safe_decimal(t.get('fee', 0))
                    if amount > 0:
                        net_amount = amount - fee
                        logger.warning(
                            f"buy_confirm 降级处理（无匹配 debit）: "
                            f"order_id={order_id}, amount={amount}, net_amount={net_amount}"
                        )
                    else:
                        logger.error(
                            f"buy_confirm 找不到匹配的 buy_debit 且无 amount 字段，"
                            f"无法计算成本: order_id={order_id}"
                        )
                        net_amount = Decimal('0')
                
                shares += confirmed_shares
                cost += net_amount
            
            elif action == 'buy':
                # 兼容旧数据：当天既扣款又确认
                # 等价于 buy_debit + buy_confirm 的原子组合
                trans_shares = safe_decimal(t.get('shares', 0))
                amount = safe_decimal(t.get('amount', 0))
                fee = safe_decimal(t.get('fee', 0))
                net_amount = amount - fee  # 净申购额（与 buy_debit + buy_confirm 逻辑一致）
                
                if trans_shares > 0:
                    shares += trans_shares
                    cost += net_amount
                else:
                    # 某些产品（如货基）用 amount 作为份额
                    shares += amount
                    cost += net_amount
            
            elif action in ('sell', 'sell_confirm'):
                # 卖出/卖出确认：份额减少，成本按比例减少
                sell_shares = safe_decimal(t.get('shares', 0))
                if sell_shares > 0 and shares > 0:
                    cost_reduction = cost * sell_shares / shares
                    cost -= cost_reduction
                    shares -= sell_shares
            
            elif action == 'dividend':
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
        
        规则：
        - buy_debit: cash_in_transit += (amount - fee)
        - buy_confirm: cash_in_transit -= 对应 debit 的 net_amount
        - buy: 不产生在途（当天确认）
        """
        product_transactions = [
            t for t in self.transactions
            if t['product_code'] == product_code and t['date'] <= asof_date
        ]
        product_transactions.sort(key=lambda x: x['date'])
        
        # 收集所有 debit
        debit_pool: Dict[str, Decimal] = {}  # order_id -> net_amount
        
        for t in product_transactions:
            action = normalize_action(t.get('action', ''))
            order_id = t.get('order_id', '').strip()
            
            if action == 'buy_debit':
                amount = safe_decimal(t.get('amount', 0))
                fee = safe_decimal(t.get('fee', 0))
                net_amount = amount - fee
                
                if order_id:
                    debit_pool[order_id] = net_amount
                else:
                    # 无 order_id 的 debit，用日期+金额作为临时 key
                    temp_key = f"_auto_{t['date']}_{amount}"
                    debit_pool[temp_key] = net_amount
            
            elif action == 'buy_confirm':
                if order_id and order_id in debit_pool:
                    del debit_pool[order_id]
            
            # buy 不产生在途（当天确认）
        
        # 在途资金 = 所有未确认 debit 的净额合计
        return sum(debit_pool.values(), Decimal('0'))
    
    def _calc_principal_total(self, product_code: str, asof_date: str) -> Decimal:
        """
        计算单个产品的累计投入本金
        
        规则：
        - buy_debit: principal += amount（扣款时计入）
        - buy: principal += amount（兼容旧数据，等价于 buy_debit 的逻辑）
        - sell: 不减少（卖出回笼不影响累计投入）
        - buy_confirm: 正常情况不增加，降级路径除外
        """
        product_transactions = [
            t for t in self.transactions
            if t['product_code'] == product_code and t['date'] <= asof_date
        ]
        product_transactions.sort(key=lambda x: x['date'])
        
        # 构建 debit 索引
        self._build_debit_index(product_code, asof_date)
        
        principal = Decimal('0')
        
        for t in product_transactions:
            action = normalize_action(t.get('action', ''))
            amount = safe_decimal(t.get('amount', 0))
            order_id = t.get('order_id', '').strip()
            
            if action == 'buy_debit':
                # 扣款时计入本金
                principal += amount
            
            elif action == 'buy':
                # 兼容旧数据，等价于 buy_debit 的逻辑
                principal += amount
            
            elif action == 'buy_confirm':
                # 降级路径时也要计入
                if order_id and order_id not in self._debit_index and amount > 0:
                    principal += amount
                    logger.warning(
                        f"buy_confirm 降级路径计入 principal_total: "
                        f"order_id={order_id}, amount={amount}"
                    )
            
            # sell, sell_confirm, dividend 都不影响 principal_total
        
        return principal
    
    def _calc_total_redemption(self, product_code: str, asof_date: str) -> Decimal:
        """
        计算单个产品的累计赎回金额（到账净额）
        
        用于计算生命周期总盈亏：
        total_pnl = total_value + total_redemption - principal_total
        
        sell/sell_confirm 的 amount 是到账净额（已扣除赎回费）
        """
        total_redemption = Decimal('0')
        
        for t in self.transactions:
            if t.get('product_code') != product_code:
                continue
            if t.get('date', '') > asof_date:
                continue
            
            action = normalize_action(t.get('action', ''))
            
            if action in ('sell', 'sell_confirm'):
                amount = safe_decimal(t.get('amount', 0))
                if amount > 0:
                    total_redemption += amount
        
        return total_redemption


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
    from data.config_loader import get_project_root
    
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
    from data.config_loader import get_project_root
    
    calc = HoldingsCalculator()
    asof = datetime.now().strftime('%Y-%m-%d')
    
    print(f"截至 {asof} 的持仓:")
    holdings = calc.get_all_holdings_data_as_of(asof)
    for code, data in holdings.items():
        print(f"  {code}:")
        print(f"    份额={data['shares']:.4f}")
        print(f"    成本={data['cost']:.2f}")
        print(f"    在途={data['cash_in_transit']:.2f}")
        print(f"    本金={data['principal_total']:.2f}")
        print(f"    赎回={data['total_redemption']:.2f}")
