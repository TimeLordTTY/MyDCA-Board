"""
回测主循环

Backtester 负责驱动整个回测流程：
1. 遍历行情数据
2. 管理现金流入
3. 调用策略获取交易信号
4. 执行交易
5. 记录结果
"""

from datetime import datetime
from typing import List, Optional, Callable, Dict, Any
import csv
import os

from .types import NavBar, DayResult
from .data_feed import DataFeed
from .portfolio import Portfolio
from ..strategies.base import Strategy, Context, Signal


# =============================================================================
#                              表格格式化工具函数
# =============================================================================

def format_table_console(
    title: str,
    headers: List[str],
    rows: List[List[Any]],
    col_widths: Optional[List[int]] = None
) -> str:
    """
    将表格数据格式化为控制台输出的字符串
    
    Args:
        title: 表格标题
        headers: 列头名称列表
        rows: 数据行列表
        col_widths: 可选的列宽列表，未指定则自动计算
    
    Returns:
        格式化后的表格字符串
    """
    if not headers:
        return ""
    
    # 将所有数据转为字符串
    str_headers = [str(h) for h in headers]
    str_rows = [[str(cell) for cell in row] for row in rows]
    
    # 计算每列宽度（取表头和数据中的最大宽度）
    if col_widths is None:
        col_widths = []
        for i, header in enumerate(str_headers):
            max_width = len(header)
            for row in str_rows:
                if i < len(row):
                    max_width = max(max_width, len(row[i]))
            col_widths.append(max_width + 2)  # 加2作为列间距
    
    # 构建输出
    lines = []
    
    # 标题
    total_width = sum(col_widths) + len(col_widths) - 1
    lines.append("")
    lines.append("=" * total_width)
    lines.append(title.center(total_width))
    lines.append("=" * total_width)
    
    # 表头
    header_line = " | ".join(
        str_headers[i].ljust(col_widths[i]) for i in range(len(str_headers))
    )
    lines.append(header_line)
    lines.append("-" * total_width)
    
    # 数据行
    for row in str_rows:
        row_cells = []
        for i in range(len(str_headers)):
            cell = row[i] if i < len(row) else ""
            row_cells.append(cell.ljust(col_widths[i]))
        lines.append(" | ".join(row_cells))
    
    lines.append("=" * total_width)
    
    return "\n".join(lines)


def format_summary_table(summary: Dict[str, Any]) -> str:
    """
    将回测摘要格式化为控制台表格字符串（键值对形式）
    
    Args:
        summary: 回测摘要字典
    
    Returns:
        格式化后的表格字符串
    """
    lines = []
    
    # 基础信息表
    basic_info = [
        ["策略名称", summary.get("strategy_name", "未知")],
        ["基金代码", summary.get("fund_code", "未知")],
        ["回测区间", f"{summary.get('start_date', 'N/A')} ~ {summary.get('end_date', 'N/A')}"],
        ["回测天数", f"{summary.get('days', 0)} 天"],
    ]
    lines.append(format_table_console("基础信息", ["项目", "数值"], basic_info))
    
    # 资金情况表
    fund_info = [
        ["累计投入本金", f"{summary.get('principal_total', 0.0):,.2f} 元"],
        ["实际买入成本", f"{summary.get('total_cost', 0.0):,.2f} 元"],
        ["期末基金市值", f"{summary.get('final_fund_value', 0.0):,.2f} 元"],
        ["期末现金余额", f"{summary.get('final_cash', 0.0):,.2f} 元"],
        ["期末总资产", f"{summary.get('final_assets', 0.0):,.2f} 元"],
    ]
    lines.append(format_table_console("资金情况", ["项目", "数值"], fund_info))
    
    # 收益情况表
    profit_info = [
        ["名义盈亏金额", f"{summary.get('nominal_pnl', 0.0):+,.2f} 元"],
        ["名义总收益率", f"{summary.get('nominal_return', 0.0) * 100:.2f}%"],
        ["真实总收益率", f"{summary.get('real_return', 0.0) * 100:.2f}%"],
        ["年化收益率", f"{summary.get('annual_return', 0.0) * 100:.2f}%"],
    ]
    lines.append(format_table_console("收益情况", ["项目", "数值"], profit_info))
    
    # 交易统计表
    trade_info = [
        ["买入次数", f"{summary.get('buy_count', 0)} 次"],
        ["卖出次数", f"{summary.get('sell_count', 0)} 次"],
        ["总买入金额", f"{summary.get('total_buy_amount', 0.0):,.2f} 元"],
        ["总卖出金额", f"{summary.get('total_sell_amount', 0.0):,.2f} 元"],
    ]
    lines.append(format_table_console("交易统计", ["项目", "数值"], trade_info))
    
    return "\n".join(lines)


def format_summary_as_row_table(
    summary: Dict[str, Any],
    fund_name: str = ""
) -> str:
    """
    将回测摘要格式化为横向表格（一行数据）
    
    Args:
        summary: 回测摘要字典
        fund_name: 基金名称（可选）
    
    Returns:
        格式化后的表格字符串
    """
    # 定义要显示的字段及其格式化方式
    fields = [
        ("基金代码", summary.get("fund_code", "未知")),
        ("基金名称", fund_name or "-"),
        ("策略名称", summary.get("strategy_name", "未知")),
        ("回测起始", summary.get("start_date", "N/A")),
        ("回测结束", summary.get("end_date", "N/A")),
        ("回测天数", f"{summary.get('days', 0)}"),
        ("累计投入本金", f"{summary.get('principal_total', 0.0):,.2f}"),
        ("实际买入成本", f"{summary.get('total_cost', 0.0):,.2f}"),
        ("期末基金市值", f"{summary.get('final_fund_value', 0.0):,.2f}"),
        ("期末现金余额", f"{summary.get('final_cash', 0.0):,.2f}"),
        ("期末总资产", f"{summary.get('final_assets', 0.0):,.2f}"),
        ("名义盈亏", f"{summary.get('nominal_pnl', 0.0):+,.2f}"),
        ("名义收益率", f"{summary.get('nominal_return', 0.0) * 100:.2f}%"),
        ("真实收益率", f"{summary.get('real_return', 0.0) * 100:.2f}%"),
        ("年化收益率", f"{summary.get('annual_return', 0.0) * 100:.2f}%"),
        ("买入次数", f"{summary.get('buy_count', 0)}"),
        ("卖出次数", f"{summary.get('sell_count', 0)}"),
        ("总买入金额", f"{summary.get('total_buy_amount', 0.0):,.2f}"),
        ("总卖出金额", f"{summary.get('total_sell_amount', 0.0):,.2f}"),
    ]
    
    # 添加策略特有字段（如果存在）
    strategy_fields = [
        ("pre_invest_locked", "预投入锁定余额"),
        ("pre_invest_total_in", "预投入累计划入"),
        ("pre_invest_released", "深跌已释放总额"),
        ("deep_dip_count", "深跌补仓次数"),
        ("last_peak_nav", "观测最高净值"),
    ]
    
    for key, label in strategy_fields:
        if key in summary:
            value = summary[key]
            if isinstance(value, float):
                if key == "last_peak_nav":
                    fields.append((label, f"{value:.4f}"))
                else:
                    fields.append((label, f"{value:,.2f}"))
            else:
                fields.append((label, str(value)))
    
    headers = [f[0] for f in fields]
    row = [f[1] for f in fields]
    
    return format_table_console("回测结果汇总", headers, [row])


def write_summary_to_csv(
    summary: Dict[str, Any],
    filepath: str,
    fund_name: str = "",
    as_row: bool = True
) -> None:
    """
    将回测摘要写入 CSV 文件
    
    Args:
        summary: 回测摘要字典
        filepath: 输出文件路径
        fund_name: 基金名称（可选）
        as_row: 是否以横向表格形式输出（一行数据），否则为键值对形式
    """
    with open(filepath, 'w', newline='', encoding='utf-8-sig') as f:
        writer = csv.writer(f)
        
        if as_row:
            # 横向表格形式（一行数据，方便对比）
            headers = [
                "基金代码", "基金名称", "策略名称",
                "回测起始", "回测结束", "回测天数",
                "累计投入本金", "实际买入成本", "期末基金市值", "期末现金余额", "期末总资产",
                "名义盈亏", "名义收益率", "真实收益率", "年化收益率",
                "买入次数", "卖出次数", "总买入金额", "总卖出金额",
            ]
            
            row = [
                summary.get("fund_code", "未知"),
                fund_name or "-",
                summary.get("strategy_name", "未知"),
                summary.get("start_date", "N/A"),
                summary.get("end_date", "N/A"),
                summary.get("days", 0),
                f"{summary.get('principal_total', 0.0):.2f}",
                f"{summary.get('total_cost', 0.0):.2f}",
                f"{summary.get('final_fund_value', 0.0):.2f}",
                f"{summary.get('final_cash', 0.0):.2f}",
                f"{summary.get('final_assets', 0.0):.2f}",
                f"{summary.get('nominal_pnl', 0.0):.2f}",
                f"{summary.get('nominal_return', 0.0) * 100:.2f}%",
                f"{summary.get('real_return', 0.0) * 100:.2f}%",
                f"{summary.get('annual_return', 0.0) * 100:.2f}%",
                summary.get("buy_count", 0),
                summary.get("sell_count", 0),
                f"{summary.get('total_buy_amount', 0.0):.2f}",
                f"{summary.get('total_sell_amount', 0.0):.2f}",
            ]
            
            # 先添加策略运行时状态字段（指标信息）
            strategy_state_fields = [
                ("pre_invest_locked", "预投入锁定余额"),
                ("pre_invest_total_in", "预投入累计划入"),
                ("pre_invest_released", "深跌已释放总额"),
                ("deep_dip_count", "深跌补仓次数"),
                ("last_peak_nav", "观测最高净值"),
                ("total_invest_count", "投资操作次数"),
                ("nav_history_len", "NAV历史长度"),
                ("low_buy_count", "低估抄底次数"),
                ("high_reduce_count", "高估减仓次数"),
            ]
            
            for key, label in strategy_state_fields:
                if key in summary:
                    headers.append(label)
                    value = summary[key]
                    if isinstance(value, float):
                        if key == "last_peak_nav":
                            row.append(f"{value:.4f}")
                        else:
                            row.append(f"{value:.2f}")
                    else:
                        row.append(str(value))
            
            # 最后添加策略配置参数（以 cfg_ 开头的字段）
            for key, value in sorted(summary.items()):
                if key.startswith("cfg_"):
                    # 去掉 cfg_ 前缀作为列名
                    label = "参数_" + key[4:]  # 添加 "参数_" 前缀便于识别
                    headers.append(label)
                    if isinstance(value, bool):
                        row.append(str(value))
                    elif isinstance(value, float):
                        if abs(value) < 1:
                            row.append(f"{value:.2%}")  # 百分比格式
                        else:
                            row.append(f"{value:.2f}")
                    else:
                        row.append(str(value))
            
            writer.writerow(headers)
            writer.writerow(row)
        else:
            # 键值对形式
            writer.writerow(["项目", "数值"])
            
            # 基础信息
            writer.writerow(["基金代码", summary.get("fund_code", "未知")])
            writer.writerow(["基金名称", fund_name or "-"])
            writer.writerow(["策略名称", summary.get("strategy_name", "未知")])
            writer.writerow(["回测起始日期", summary.get("start_date", "N/A")])
            writer.writerow(["回测结束日期", summary.get("end_date", "N/A")])
            writer.writerow(["回测天数", summary.get("days", 0)])
            writer.writerow([])
            
            # 资金情况
            writer.writerow(["累计投入本金", f"{summary.get('principal_total', 0.0):.2f}"])
            writer.writerow(["实际买入成本", f"{summary.get('total_cost', 0.0):.2f}"])
            writer.writerow(["期末基金市值", f"{summary.get('final_fund_value', 0.0):.2f}"])
            writer.writerow(["期末现金余额", f"{summary.get('final_cash', 0.0):.2f}"])
            writer.writerow(["期末总资产", f"{summary.get('final_assets', 0.0):.2f}"])
            writer.writerow([])
            
            # 收益情况
            writer.writerow(["名义盈亏金额", f"{summary.get('nominal_pnl', 0.0):.2f}"])
            writer.writerow(["名义总收益率", f"{summary.get('nominal_return', 0.0) * 100:.2f}%"])
            writer.writerow(["真实总收益率", f"{summary.get('real_return', 0.0) * 100:.2f}%"])
            writer.writerow(["年化收益率", f"{summary.get('annual_return', 0.0) * 100:.2f}%"])
            writer.writerow([])
            
            # 交易统计
            writer.writerow(["买入次数", summary.get("buy_count", 0)])
            writer.writerow(["卖出次数", summary.get("sell_count", 0)])
            writer.writerow(["总买入金额", f"{summary.get('total_buy_amount', 0.0):.2f}"])
            writer.writerow(["总卖出金额", f"{summary.get('total_sell_amount', 0.0):.2f}"])
            
            # 策略配置参数（以 cfg_ 开头的字段）
            cfg_fields = [(k, v) for k, v in sorted(summary.items()) if k.startswith("cfg_")]
            if cfg_fields:
                writer.writerow([])
                writer.writerow(["# 策略配置参数"])
                for key, value in cfg_fields:
                    label = key[4:]  # 移除 "cfg_" 前缀
                    if isinstance(value, bool):
                        writer.writerow([label, str(value)])
                    elif isinstance(value, float):
                        if abs(value) < 1:
                            writer.writerow([label, f"{value:.2%}"])
                        else:
                            writer.writerow([label, f"{value:.2f}"])
                    else:
                        writer.writerow([label, str(value)])
            
            # 策略运行时状态字段
            strategy_state_fields = [
                ("pre_invest_locked", "预投入锁定余额"),
                ("pre_invest_total_in", "预投入累计划入"),
                ("pre_invest_released", "深跌已释放总额"),
                ("deep_dip_count", "深跌补仓次数"),
                ("last_peak_nav", "观测最高净值"),
                ("total_invest_count", "投资操作次数"),
                ("nav_history_len", "NAV历史长度"),
                ("low_buy_count", "低估抄底次数"),
                ("high_reduce_count", "高估减仓次数"),
            ]
            
            has_state_fields = any(key in summary for key, _ in strategy_state_fields)
            if has_state_fields:
                writer.writerow([])
                writer.writerow(["# 策略运行时状态"])
                for key, label in strategy_state_fields:
                    if key in summary:
                        value = summary[key]
                        if isinstance(value, float):
                            if key == "last_peak_nav":
                                writer.writerow([label, f"{value:.4f}"])
                            else:
                                writer.writerow([label, f"{value:.2f}"])
                        else:
                            writer.writerow([label, str(value)])


def write_table_to_csv(
    title: str,
    headers: List[str],
    rows: List[List[Any]],
    filepath: str
) -> None:
    """
    将单个表格写入 CSV 文件
    
    Args:
        title: 表格标题（会作为文件第一行注释）
        headers: 列头名称列表
        rows: 数据行列表
        filepath: 输出文件路径
    """
    with open(filepath, 'w', newline='', encoding='utf-8-sig') as f:
        writer = csv.writer(f)
        # 写入标题作为第一行
        writer.writerow([f"# {title}"])
        writer.writerow(headers)
        for row in rows:
            writer.writerow(row)


def write_tables_to_csv(
    tables: List[Dict[str, Any]],
    output_dir: str,
    prefix: str = ""
) -> List[str]:
    """
    将多个表格写入 CSV 文件
    
    Args:
        tables: 表格数据列表，每个字典包含 title, headers, rows
        output_dir: 输出目录
        prefix: 文件名前缀
    
    Returns:
        生成的文件路径列表
    """
    os.makedirs(output_dir, exist_ok=True)
    filepaths = []
    
    for i, table in enumerate(tables):
        title = table.get("title", f"表格{i+1}")
        headers = table.get("headers", [])
        rows = table.get("rows", [])
        
        if not headers or not rows:
            continue
        
        # 生成文件名（使用标题的安全版本）
        safe_title = "".join(c if c.isalnum() or c in ('_', '-') else '_' for c in title)
        filename = f"{prefix}_{safe_title}.csv" if prefix else f"{safe_title}.csv"
        filepath = os.path.join(output_dir, filename)
        
        write_table_to_csv(title, headers, rows, filepath)
        filepaths.append(filepath)
    
    return filepaths


# =============================================================================
#                              回测辅助函数
# =============================================================================

def is_new_month(prev_date: Optional[datetime], curr_date: datetime) -> bool:
    """
    判断是否进入新的一个月
    
    Args:
        prev_date: 前一个交易日日期，首日为 None
        curr_date: 当前交易日日期
    
    Returns:
        True 如果是新月份的第一个交易日
    """
    if prev_date is None:
        return True
    return curr_date.year > prev_date.year or curr_date.month > prev_date.month


def is_new_week(prev_date: Optional[datetime], curr_date: datetime) -> bool:
    """
    判断是否进入新的一周
    
    Args:
        prev_date: 前一个交易日日期
        curr_date: 当前交易日日期
    
    Returns:
        True 如果是新一周的第一个交易日
    """
    if prev_date is None:
        return True
    # ISO 周数比较
    prev_week = prev_date.isocalendar()[1]
    curr_week = curr_date.isocalendar()[1]
    return curr_week != prev_week or curr_date.year != prev_date.year


class Backtester:
    """
    回测引擎
    
    负责驱动回测流程，但不包含任何策略逻辑
    
    Attributes:
        data_feed: 行情数据源
        portfolio: 投资组合
        strategy: 策略实例
        initial_invest: 初始一次性投入金额
        periodic_invest: 定期投入金额
        invest_day_rule: 定投周期规则 ("month_change" 或 "week_change")
        start_date: 回测起始日期（格式：YYYY-MM-DD），为 None 则从数据起始开始
        end_date: 回测结束日期（格式：YYYY-MM-DD），为 None 则到数据结束
    """
    
    def __init__(
        self,
        data_feed: DataFeed,
        portfolio: Portfolio,
        strategy: Strategy,
        initial_invest: float = 0.0,
        periodic_invest: float = 0.0,
        invest_day_rule: str = "month_change",
        start_date: Optional[str] = None,
        end_date: Optional[str] = None,
        fund_code: str = "未知"
    ):
        """
        初始化回测引擎
        
        Args:
            data_feed: 行情数据源
            portfolio: 投资组合对象
            strategy: 策略实例
            initial_invest: 首次投入金额，在第一个交易日买入
            periodic_invest: 定期投入金额
            invest_day_rule: 定投触发规则
                - "month_change": 每月第一个交易日
                - "week_change": 每周第一个交易日
            start_date: 回测起始日期（格式：YYYY-MM-DD），为 None 则从数据起始开始
            end_date: 回测结束日期（格式：YYYY-MM-DD），为 None 则到数据结束
            fund_code: 基金代码（用于标识和展示）
        """
        # 保存原始数据信息
        self.original_start_date = data_feed.start_date
        self.original_end_date = data_feed.end_date
        self.config_start_date = start_date
        self.config_end_date = end_date
        self.fund_code = fund_code
        
        # 对数据进行日期切片
        self.data_feed = self._slice_data_by_date(data_feed, start_date, end_date)
        
        self.portfolio = portfolio
        self.strategy = strategy
        self.initial_invest = initial_invest
        self.periodic_invest = periodic_invest
        self.invest_day_rule = invest_day_rule
        
        # 内部状态
        self.results: List[DayResult] = []
        self.cash_pool = 0.0  # 待投资现金池
        self.principal_total = 0.0  # 真实打入本金总额（包括初始投资和定期投资）
    
    def _slice_data_by_date(
        self,
        data_feed: DataFeed,
        start_date: Optional[str],
        end_date: Optional[str]
    ) -> DataFeed:
        """
        根据日期区间对数据进行切片
        
        Args:
            data_feed: 原始数据源
            start_date: 起始日期字符串（格式：YYYY-MM-DD），为 None 则不限制起始
            end_date: 结束日期字符串（格式：YYYY-MM-DD），为 None 则不限制结束
        
        Returns:
            切片后的新 DataFeed 对象
        
        Raises:
            ValueError: 如果日期格式错误或切片后数据为空
        """
        bars = data_feed.bars
        
        # 如果没有指定任何日期限制，直接返回原数据源
        if start_date is None and end_date is None:
            return data_feed
        
        # 解析日期字符串
        start_dt = None
        end_dt = None
        
        try:
            if start_date is not None:
                start_dt = datetime.strptime(start_date, "%Y-%m-%d")
            if end_date is not None:
                end_dt = datetime.strptime(end_date, "%Y-%m-%d")
        except ValueError as e:
            raise ValueError(f"日期格式错误，应为 YYYY-MM-DD 格式: {e}")
        
        # 过滤数据
        filtered_bars = []
        for bar in bars:
            # 检查起始日期
            if start_dt is not None and bar.date < start_dt:
                continue
            # 检查结束日期
            if end_dt is not None and bar.date > end_dt:
                continue
            filtered_bars.append(bar)
        
        # 验证切片后数据非空
        if not filtered_bars:
            date_range_str = ""
            if start_date and end_date:
                date_range_str = f"区间 [{start_date}, {end_date}]"
            elif start_date:
                date_range_str = f"起始日期 {start_date} 之后"
            elif end_date:
                date_range_str = f"结束日期 {end_date} 之前"
            
            raise ValueError(
                f"指定的日期{date_range_str}内没有数据。"
                f"原始数据区间: [{self.original_start_date.strftime('%Y-%m-%d')}, "
                f"{self.original_end_date.strftime('%Y-%m-%d')}]"
            )
        
        # 返回新的 DataFeed
        return DataFeed(filtered_bars)
    
    def _check_invest_day(
        self, 
        prev_date: Optional[datetime], 
        curr_date: datetime
    ) -> bool:
        """
        检查是否是定投日
        
        Args:
            prev_date: 前一个交易日
            curr_date: 当前交易日
        
        Returns:
            True 如果是定投日
        """
        if self.invest_day_rule == "month_change":
            return is_new_month(prev_date, curr_date)
        elif self.invest_day_rule == "week_change":
            return is_new_week(prev_date, curr_date)
        else:
            return False
    
    def run(self) -> List[DayResult]:
        """
        执行回测
        
        回测流程：
        1. 首日进行初始投资
        2. 遍历每个交易日：
           a. 检查是否有定投资金流入
           b. 更新组合估值
           c. 调用策略获取信号
           d. 执行交易
           e. 记录结果
        
        Returns:
            每日回测结果列表
        """
        self.results = []
        self.cash_pool = 0.0
        self.principal_total = 0.0  # 重置真实打入本金总额
        prev_date: Optional[datetime] = None
        is_first_day = True
        
        # 调用策略的 on_start
        self.strategy.on_start()
        
        for bar in self.data_feed:
            date, nav = bar.date, bar.nav
            
            # ===== 1. 处理资金流入 =====
            cash_inflow = 0.0
            
            # 首日处理初始投资
            if is_first_day and self.initial_invest > 0:
                cash_inflow += self.initial_invest
                self.cash_pool += self.initial_invest
                self.principal_total += self.initial_invest  # 记录真实打入本金
                is_first_day = False
            else:
                is_first_day = False
            
            # 检查定投日
            if self._check_invest_day(prev_date, date) and self.periodic_invest > 0:
                # 首日的定投资金不重复计算
                if prev_date is not None:
                    cash_inflow += self.periodic_invest
                    self.cash_pool += self.periodic_invest
                    self.principal_total += self.periodic_invest  # 记录真实打入本金
            
            # ===== 2. 更新组合估值 =====
            self.portfolio.update_valuation(nav)
            
            # ===== 3. 构造上下文，调用策略 =====
            ctx = Context(
                date=date,
                nav=nav,
                portfolio=self.portfolio,
                cash_inflow=cash_inflow,
                cash_pool=self.cash_pool,
                state=self.strategy.state,
            )
            signal = self.strategy.on_bar(ctx)
            
            # ===== 4. 执行交易 =====
            buy_cash = 0.0
            sell_cash = 0.0
            buy_units = 0.0
            sell_units = 0.0
            
            # 处理买入
            actual_buy_cash = min(signal.buy_cash, self.cash_pool)
            if actual_buy_cash > 0:
                buy_units = self.portfolio.buy(nav, actual_buy_cash)
                buy_cash = actual_buy_cash
                self.cash_pool -= actual_buy_cash
            
            # 处理卖出
            if signal.sell_units > 0:
                sell_cash = self.portfolio.sell(nav, signal.sell_units)
                sell_units = min(signal.sell_units, self.portfolio.units + signal.sell_units)
                # 卖出所得加入现金池
                self.cash_pool += sell_cash
            
            # ===== 5. 再次更新估值 =====
            self.portfolio.update_valuation(nav)
            
            # ===== 6. 记录结果 =====
            result = DayResult(
                date=date,
                nav=nav,
                units=self.portfolio.units,
                fund_value=self.portfolio.market_value,
                cash=self.cash_pool,
                total_value=self.portfolio.market_value + self.cash_pool,
                total_cost=self.portfolio.total_cost,
                unrealized_pnl=self.portfolio.market_value + self.cash_pool - self.portfolio.total_cost,
                unrealized_pnl_pct=(
                    (self.portfolio.market_value + self.cash_pool - self.portfolio.total_cost) 
                    / self.portfolio.total_cost
                ) if self.portfolio.total_cost > 0 else 0.0,
                buy_cash=buy_cash,
                sell_cash=sell_cash,
                buy_units=buy_units,
                sell_units=sell_units,
                note=signal.note,
            )
            self.results.append(result)
            
            prev_date = date
        
        # 调用策略的 on_end
        self.strategy.on_end()
        
        return self.results
    
    def get_summary(self) -> dict:
        """
        获取回测摘要统计
        
        Returns:
            包含关键指标的字典，包括原始数据区间和实际回测区间
        """
        if not self.results:
            return {}
        
        first = self.results[0]
        last = self.results[-1]
        
        # 计算年化收益率
        days = (last.date - first.date).days
        years = days / 365.0 if days > 0 else 1
        total_return = last.unrealized_pnl_pct
        annual_return = (1 + total_return) ** (1 / years) - 1 if years > 0 else 0
        
        # 统计交易次数
        buy_count = sum(1 for r in self.results if r.buy_cash > 0)
        sell_count = sum(1 for r in self.results if r.sell_cash > 0)
        total_buy = sum(r.buy_cash for r in self.results)
        total_sell = sum(r.sell_cash for r in self.results)
        
        # 从策略中获取额外统计
        strategy_stats = {}
        if hasattr(self.strategy, 'get_stats'):
            strategy_stats = self.strategy.get_stats()
        
        # 计算真实收益率（基于 principal_total）
        real_return = (last.total_value - self.principal_total) / self.principal_total if self.principal_total > 0 else 0.0
        nominal_pnl = last.total_value - last.total_cost
        
        return {
            # 基础信息
            'strategy_name': self.strategy.get_name(),
            'fund_code': self.fund_code,
            'start_date': first.date.strftime('%Y-%m-%d'),
            'end_date': last.date.strftime('%Y-%m-%d'),
            'days': days,
            
            # 日期区间信息
            'data_start_date': self.original_start_date.strftime('%Y-%m-%d') if self.original_start_date else None,
            'data_end_date': self.original_end_date.strftime('%Y-%m-%d') if self.original_end_date else None,
            'backtest_start_date': first.date.strftime('%Y-%m-%d'),
            'backtest_end_date': last.date.strftime('%Y-%m-%d'),
            
            # 资金情况
            'principal_total': self.principal_total,  # 真实打入本金总额
            'total_cost': last.total_cost,  # 实际买入成本（已下场的资金）
            'final_fund_value': last.fund_value,  # 期末基金市值
            'final_cash': last.cash,  # 期末现金余额
            'final_assets': last.total_value,  # 期末总资产
            
            # 收益情况
            'nominal_pnl': nominal_pnl,  # 基于 total_cost 的名义盈亏
            'nominal_return': total_return,  # 基于 total_cost 的名义收益率
            'real_return': real_return,  # 基于 principal_total 的真实收益率
            'annual_return': annual_return,  # 年化收益率
            
            # 兼容旧字段
            'total_return': total_return,
            'final_value': last.total_value,
            
            # 交易统计
            'buy_count': buy_count,
            'sell_count': sell_count,
            'total_buy_amount': total_buy,
            'total_sell_amount': total_sell,
            
            # 兼容旧字段
            'total_buy': total_buy,
            'total_sell': total_sell,
            
            # 策略自定义统计
            **strategy_stats,
        }
    
    def print_summary_table(self) -> None:
        """
        在控制台打印回测摘要表格
        """
        summary = self.get_summary()
        if not summary:
            print("无回测结果可显示")
            return
        
        print(format_summary_table(summary))
    
    def print_strategy_tables(self) -> None:
        """
        在控制台打印策略自定义表格
        """
        tables = self.strategy.get_result_tables()
        if not tables:
            return
        
        for table in tables:
            title = table.get("title", "表格")
            headers = table.get("headers", [])
            rows = table.get("rows", [])
            
            if headers and rows:
                print(format_table_console(title, headers, rows))
    
    def export_results(
        self,
        output_dir: str,
        prefix: str = "",
        fund_name: str = "",
        print_to_console: bool = True,
        save_to_csv: bool = True
    ) -> Dict[str, Any]:
        """
        统一导出回测结果，支持控制台打印和 CSV 文件输出
        
        Args:
            output_dir: 输出目录
            prefix: 文件名前缀（如基金代码_策略类型）
            fund_name: 基金名称（可选）
            print_to_console: 是否在控制台打印表格
            save_to_csv: 是否保存到 CSV 文件
        
        Returns:
            包含导出结果信息的字典:
            - summary: 摘要字典
            - strategy_tables: 策略自定义表格列表
            - files: 生成的文件路径字典
        """
        os.makedirs(output_dir, exist_ok=True)
        
        summary = self.get_summary()
        strategy_tables = self.strategy.get_result_tables()
        files = {}
        
        # 控制台打印
        if print_to_console:
            # 打印横向摘要表格
            if summary:
                print(format_summary_as_row_table(summary, fund_name))
            
            # 打印策略自定义表格
            for table in strategy_tables:
                title = table.get("title", "表格")
                headers = table.get("headers", [])
                rows = table.get("rows", [])
                
                if headers and rows:
                    print(format_table_console(title, headers, rows))
        
        # CSV 文件输出
        if save_to_csv:
            # 保存摘要
            if summary:
                summary_file = os.path.join(output_dir, f"{prefix}_summary.csv" if prefix else "summary.csv")
                write_summary_to_csv(summary, summary_file, fund_name, as_row=True)
                files["summary"] = summary_file
            
            # 保存每日明细
            if self.results:
                details_file = os.path.join(output_dir, f"{prefix}_details.csv" if prefix else "details.csv")
                write_results_to_csv(self.results, details_file)
                files["details"] = details_file
            
            # 保存策略自定义表格
            if strategy_tables:
                table_files = write_tables_to_csv(strategy_tables, output_dir, prefix)
                files["strategy_tables"] = table_files
        
        return {
            "summary": summary,
            "strategy_tables": strategy_tables,
            "files": files,
        }


def write_results_to_csv(results: List[DayResult], filepath: str) -> None:
    """
    将回测结果写入CSV文件
    
    Args:
        results: DayResult 列表
        filepath: 输出文件路径
    """
    if not results:
        return
    
    fieldnames = [
        'date', 'nav', 'units', 'fund_value', 'cash', 
        'total_value', 'total_cost', 'unrealized_pnl', 'unrealized_pnl_pct',
        'buy_cash', 'sell_cash', 'buy_units', 'sell_units', 'note'
    ]
    
    with open(filepath, 'w', newline='', encoding='utf-8-sig') as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        
        for r in results:
            writer.writerow({
                'date': r.date.strftime('%Y-%m-%d'),
                'nav': f'{r.nav:.4f}',
                'units': f'{r.units:.4f}',
                'fund_value': f'{r.fund_value:.2f}',
                'cash': f'{r.cash:.2f}',
                'total_value': f'{r.total_value:.2f}',
                'total_cost': f'{r.total_cost:.2f}',
                'unrealized_pnl': f'{r.unrealized_pnl:.2f}',
                'unrealized_pnl_pct': f'{r.unrealized_pnl_pct:.4f}',
                'buy_cash': f'{r.buy_cash:.2f}',
                'sell_cash': f'{r.sell_cash:.2f}',
                'buy_units': f'{r.buy_units:.4f}',
                'sell_units': f'{r.sell_units:.4f}',
                'note': r.note,
            })

