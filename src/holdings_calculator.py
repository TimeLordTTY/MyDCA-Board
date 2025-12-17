"""持仓计算模块 - 增量模式：基础持仓 + 交易流水

支持的交易类型 (action):
  - buy: 买入，份额增加，成本增加
  - sell: 卖出，份额减少，成本按比例减少
  - dividend: 分红，份额增加，成本不变（免费获得的份额）

CSV 格式 (data/transactions.csv):
  date,product_code,action,amount,shares,fee,nav,nav_date,note
  
  - buy/sell: 所有字段都需要
  - dividend: 只需要 date, product_code, action, shares, note（其他字段可留空或填0）
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
                product_id = item.get('products_id', '')
                amount = safe_decimal(item.get('amount', 0))
                if product_id:
                    base_holdings[product_id] = amount
    except Exception as e:
        logger.warning(f"加载 holdings.json 失败: {e}")
    
    return base_holdings


def calc_position_incremental(
    product_code: str,
    asof_date: str,
    transactions_path: Path = None,
    holdings_path: Path = None
) -> Tuple[Decimal, Decimal]:
    """
    增量模式计算持仓：基础份额 (holdings.json) + 交易流水累计变更
    
    :param product_code: 产品代码
    :param asof_date: 截止日期 (YYYY-MM-DD)
    :param transactions_path: 交易流水文件路径
    :param holdings_path: holdings.json 文件路径
    :return: (shares_total, cost_total)
    
    计算逻辑：
    - 基础份额来自 holdings.json
    - 在基础上累加 transactions.csv 的增减
    - 成本仅从交易流水计算（基础持仓成本未知，设为0）
    """
    from config_loader import get_project_root
    
    if transactions_path is None:
        transactions_path = get_project_root() / "data" / "transactions.csv"
    
    # 1. 获取基础份额
    base_holdings = load_base_holdings(holdings_path)
    base_shares = base_holdings.get(product_code, Decimal('0'))
    
    # 2. 加载交易流水
    all_transactions = load_transactions(transactions_path)
    
    # 筛选该产品、截止日期前的交易
    product_transactions = [
        t for t in all_transactions
        if t['product_code'] == product_code and t['date'] <= asof_date
    ]
    
    # 按日期排序
    product_transactions.sort(key=lambda x: x['date'])
    
    # 3. 累计份额变更和成本
    shares_delta = Decimal('0')
    cost_total = Decimal('0')
    
    for t in product_transactions:
        action = t['action'].upper()
        amount = safe_decimal(t.get('amount', 0))  # 交易金额
        shares = safe_decimal(t.get('shares', 0))  # 交易份额
        fee = safe_decimal(t.get('fee', 0))        # 手续费
        
        if action == 'BUY':
            # 买入：份额增加，成本增加
            if shares > 0:
                shares_delta += shares
                cost_total += amount + fee
            else:
                # 如果没有指定份额，用金额作为份额（某些产品如货基）
                shares_delta += amount
                cost_total += amount + fee
                
        elif action == 'SELL':
            # 卖出：份额减少，成本按比例减少
            current_shares = base_shares + shares_delta
            if shares > 0:
                if current_shares > 0 and cost_total > 0:
                    # 按份额比例减少成本
                    cost_reduction = cost_total * shares / current_shares
                    cost_total -= cost_reduction
                shares_delta -= shares
            else:
                if current_shares > 0 and cost_total > 0:
                    cost_reduction = cost_total * amount / current_shares
                    cost_total -= cost_reduction
                shares_delta -= amount
        
        elif action == 'DIVIDEND':
            # 分红：份额增加，成本不变（免费获得的份额）
            if shares > 0:
                shares_delta += shares
    
    # 4. 计算最终份额
    shares_total = base_shares + shares_delta
    
    # 确保份额和成本不为负
    if shares_total < 0:
        logger.warning(f"产品 {product_code} 计算出负份额 {shares_total}，设为0")
        shares_total = Decimal('0')
    if cost_total < 0:
        cost_total = Decimal('0')
    
    return shares_total, cost_total


def calc_position_from_transactions(
    product_code: str,
    asof_date: str,
    transactions_path: Path = None
) -> Tuple[Optional[Decimal], Optional[Decimal]]:
    """
    纯交易流水模式：仅从交易流水计算持仓（不使用 holdings.json）
    
    :param product_code: 产品代码
    :param asof_date: 截止日期 (YYYY-MM-DD)
    :param transactions_path: 交易流水文件路径（可选）
    :return: (shares_total, cost_total) 或 (None, None) 如果无流水
    """
    from config_loader import get_project_root
    
    if transactions_path is None:
        transactions_path = get_project_root() / "data" / "transactions.csv"
    
    all_transactions = load_transactions(transactions_path)
    
    # 筛选该产品、截止日期前的交易
    product_transactions = [
        t for t in all_transactions
        if t['product_code'] == product_code and t['date'] <= asof_date
    ]
    
    if not product_transactions:
        return None, None
    
    # 按日期排序
    product_transactions.sort(key=lambda x: x['date'])
    
    shares_total = Decimal('0')
    cost_total = Decimal('0')
    
    for t in product_transactions:
        action = t['action'].upper()
        amount = safe_decimal(t.get('amount', 0))  # 交易金额
        shares = safe_decimal(t.get('shares', 0))  # 交易份额
        fee = safe_decimal(t.get('fee', 0))        # 手续费
        
        if action == 'BUY':
            if shares > 0:
                shares_total += shares
                cost_total += amount + fee
            else:
                shares_total += amount
                cost_total += amount + fee
                
        elif action == 'SELL':
            if shares > 0:
                if shares_total > 0:
                    cost_reduction = cost_total * shares / shares_total
                    cost_total -= cost_reduction
                shares_total -= shares
            else:
                if shares_total > 0:
                    cost_reduction = cost_total * amount / shares_total
                    cost_total -= cost_reduction
                shares_total -= amount
        
        elif action == 'DIVIDEND':
            # 分红：份额增加，成本不变（免费获得的份额）
            if shares > 0:
                shares_total += shares
    
    if shares_total < 0:
        logger.warning(f"产品 {product_code} 计算出负份额 {shares_total}，设为0")
        shares_total = Decimal('0')
    if cost_total < 0:
        cost_total = Decimal('0')
    
    return shares_total, cost_total


def get_all_product_positions(asof_date: str, transactions_path: Path = None) -> Dict[str, Tuple[Decimal, Decimal]]:
    """
    获取所有有交易记录的产品的持仓
    :return: {product_code: (shares, cost)}
    """
    from config_loader import get_project_root
    
    if transactions_path is None:
        transactions_path = get_project_root() / "data" / "transactions.csv"
    
    all_transactions = load_transactions(transactions_path)
    
    # 获取所有有交易的产品
    product_codes = set(t['product_code'] for t in all_transactions if t['date'] <= asof_date)
    
    positions = {}
    for product_code in product_codes:
        shares, cost = calc_position_from_transactions(product_code, asof_date, transactions_path)
        if shares is not None:
            positions[product_code] = (shares, cost)
    
    return positions


def has_transactions(product_code: str, transactions_path: Path = None) -> bool:
    """
    检查某产品是否有交易流水
    """
    from config_loader import get_project_root
    
    if transactions_path is None:
        transactions_path = get_project_root() / "data" / "transactions.csv"
    
    all_transactions = load_transactions(transactions_path)
    return any(t['product_code'] == product_code for t in all_transactions)


if __name__ == "__main__":
    # 简单测试
    from config_loader import get_project_root
    
    transactions_path = get_project_root() / "data" / "transactions.csv"
    print(f"交易流水文件: {transactions_path}")
    print(f"文件存在: {transactions_path.exists()}")
    
    transactions = load_transactions(transactions_path)
    print(f"交易记录数: {len(transactions)}")
    
    if transactions:
        # 获取第一个产品的持仓
        first_product = transactions[0]['product_code']
        shares, cost = calc_position_from_transactions(first_product, '2099-12-31')
        print(f"产品 {first_product}: 份额={shares}, 成本={cost}")

