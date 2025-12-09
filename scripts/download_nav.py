"""
基金净值下载工具

从天天基金(东方财富)下载基金历史净值数据，保存为回测引擎可直接使用的 CSV 格式

使用示例：
    python download_nav.py 163406                    # 下载全部历史
    python download_nav.py 163406 --start 2020-01-01 # 指定起始日期
    python download_nav.py 163406 --start 2020-01-01 --end 2024-12-31
    python download_nav.py 163406 --list             # 只查看基金信息，不下载
"""

import argparse
import requests
import csv
import time
import json
import os
import sys
from datetime import datetime
from typing import List, Dict, Optional


# 数据保存目录（相对于项目根目录）
DATA_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "data", "nav")


def get_fund_info(fund_code: str) -> Optional[Dict]:
    """
    获取基金基本信息
    
    Args:
        fund_code: 基金代码
    
    Returns:
        基金信息字典，包含名称、类型等
    """
    url = "https://fundgz.1234567.com.cn/js/{}.js".format(fund_code)
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)",
        "Referer": "https://fund.eastmoney.com/"
    }
    
    try:
        resp = requests.get(url, headers=headers, timeout=10)
        resp.raise_for_status()
        
        # 响应格式：jsonpgz({"fundcode":"163406","name":"兴全合润混合(LOF)",...});
        text = resp.text
        if text.startswith("jsonpgz(") and text.endswith(");"):
            json_str = text[8:-2]
            data = json.loads(json_str)
            return {
                "code": data.get("fundcode"),
                "name": data.get("name"),
                "nav": data.get("dwjz"),          # 最新净值
                "nav_date": data.get("jzrq"),     # 净值日期
                "estimate": data.get("gsz"),      # 估算净值
                "estimate_change": data.get("gszzl"),  # 估算涨跌幅
            }
    except Exception as e:
        print(f"⚠️ 获取基金信息失败: {e}")
    
    return None


def fetch_fund_nav(
    fund_code: str,
    start_date: str = "",
    end_date: str = "",
    page_size: int = 20,
    verbose: bool = True
) -> List[Dict]:
    """
    从天天基金(东财)拉取某只基金的历史净值数据

    Args:
        fund_code: 基金代码，比如 '163406'
        start_date: 起始日期 'YYYY-MM-DD'，留空表示最早开始
        end_date: 结束日期 'YYYY-MM-DD'，留空表示最新
        page_size: 每页条数，API 实际最多返回 20 条
        verbose: 是否打印进度信息
    
    Returns:
        列表，每个元素是 dict: {
            'date': 日期,
            'nav': 单位净值,
            'accum_nav': 累计净值,
            'change_pct': 涨跌幅（%）
        }
    """
    url = "https://api.fund.eastmoney.com/f10/lsjz"
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)",
        "Referer": "https://fundf10.eastmoney.com/"
    }
    params = {
        "fundCode": fund_code,
        "pageIndex": 1,
        "pageSize": page_size,
        "startDate": start_date,
        "endDate": end_date,
    }

    all_rows = []

    while True:
        try:
            resp = requests.get(url, headers=headers, params=params, timeout=15)
            resp.raise_for_status()
        except requests.RequestException as e:
            print(f"❌ 请求失败: {e}")
            break

        data = resp.json()
        data_obj = data.get("Data", {})
        
        # 有时候 Data 返回的是 JSON 字符串而非对象
        if isinstance(data_obj, str):
            data_obj = json.loads(data_obj) if data_obj else {}
        
        nav_list = data_obj.get("LSJZList", [])

        if not nav_list:
            break

        for item in nav_list:
            date = item.get("FSRQ")           # 日期
            nav = item.get("DWJZ")            # 单位净值
            accum_nav = item.get("LJJZ")      # 累计净值
            change_pct = item.get("JZZZL")    # 日涨跌幅

            if not date or not nav:
                continue

            all_rows.append({
                "date": date,
                "nav": nav,
                "accum_nav": accum_nav,
                "change_pct": change_pct if change_pct else "0.00"
            })

        # 如果返回条数少于请求的 pageSize，说明已经是最后一页
        if len(nav_list) < page_size:
            break

        params["pageIndex"] += 1
        if verbose:
            print(f"  📥 已获取第 {params['pageIndex'] - 1} 页，累计 {len(all_rows)} 条...")
        
        # 礼貌请求，避免被限流
        time.sleep(0.3)

    # 天天基金返回的是按日期倒序，反转成从旧到新
    all_rows.reverse()
    return all_rows


def save_to_csv(rows: List[Dict], filepath: str) -> None:
    """
    保存净值数据到 CSV 文件
    
    输出格式与回测引擎兼容：
    - date: 日期 (YYYY-MM-DD)
    - nav: 单位净值
    - accum_nav: 累计净值
    - change_pct: 涨跌幅(%)
    
    Args:
        rows: 净值数据列表
        filepath: 输出文件路径
    """
    # 确保目录存在
    os.makedirs(os.path.dirname(filepath), exist_ok=True)
    
    fieldnames = ["date", "nav", "accum_nav", "change_pct"]
    
    with open(filepath, "w", newline="", encoding="utf-8-sig") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        for r in rows:
            writer.writerow(r)


def main():
    """命令行入口"""
    parser = argparse.ArgumentParser(
        description="基金净值下载工具 - 下载历史净值数据供回测引擎使用",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
示例：
  python download_nav.py 163406                         # 下载全部历史
  python download_nav.py 163406 --start 2020-01-01      # 从 2020 年开始
  python download_nav.py 163406 -s 2020-01-01 -e 2024-12-31  # 指定范围
  python download_nav.py 163406 --info                  # 只查看基金信息

常用基金代码：
  163406  兴全合润混合(LOF)
  110011  易方达中小盘混合
  000961  天弘沪深300ETF联接
  001156  申万菱信中证申万证券
"""
    )
    
    parser.add_argument(
        "fund_code",
        type=str,
        help="基金代码，如 163406"
    )
    
    parser.add_argument(
        "-s", "--start",
        type=str,
        default="",
        help="起始日期 (YYYY-MM-DD)，默认从最早开始"
    )
    
    parser.add_argument(
        "-e", "--end",
        type=str,
        default="",
        help="结束日期 (YYYY-MM-DD)，默认到最新"
    )
    
    parser.add_argument(
        "-o", "--output",
        type=str,
        default="",
        help="输出文件路径，默认保存到 data/nav/<fund_code>.csv"
    )
    
    parser.add_argument(
        "--info",
        action="store_true",
        help="只显示基金信息，不下载数据"
    )
    
    args = parser.parse_args()
    fund_code = args.fund_code.strip()
    
    # 获取基金信息
    print(f"\n🔍 查询基金 {fund_code} ...")
    fund_info = get_fund_info(fund_code)
    
    if fund_info:
        print(f"   基金名称: {fund_info.get('name', '未知')}")
        print(f"   最新净值: {fund_info.get('nav', '-')} ({fund_info.get('nav_date', '-')})")
        if fund_info.get('estimate'):
            print(f"   估算净值: {fund_info.get('estimate')} ({fund_info.get('estimate_change', '-')}%)")
    else:
        print(f"   ⚠️ 无法获取基金信息，继续尝试下载...")
    
    if args.info:
        return
    
    # 下载数据
    print(f"\n📊 开始下载净值数据...")
    if args.start:
        print(f"   起始日期: {args.start}")
    if args.end:
        print(f"   结束日期: {args.end}")
    
    rows = fetch_fund_nav(
        fund_code,
        start_date=args.start,
        end_date=args.end
    )
    
    if not rows:
        print("❌ 未获取到任何数据，请检查基金代码是否正确")
        sys.exit(1)
    
    # 确定输出路径
    if args.output:
        output_path = args.output
    else:
        output_path = os.path.join(DATA_DIR, f"{fund_code}.csv")
    
    # 保存数据
    save_to_csv(rows, output_path)
    
    # 输出统计
    first_date = rows[0]["date"]
    last_date = rows[-1]["date"]
    first_nav = float(rows[0]["nav"])
    last_nav = float(rows[-1]["nav"])
    total_return = (last_nav - first_nav) / first_nav * 100
    
    print(f"\n✅ 下载完成！")
    print(f"   保存路径: {output_path}")
    print(f"   数据条数: {len(rows)} 条")
    print(f"   时间范围: {first_date} ~ {last_date}")
    print(f"   净值变化: {first_nav:.4f} → {last_nav:.4f} ({total_return:+.2f}%)")
    
    # 提示如何使用
    rel_path = os.path.relpath(output_path, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    print(f"\n💡 使用回测引擎:")
    print(f"   cd core/backtest")
    print(f"   python main.py --csv ../../{rel_path} --fund {fund_code} --strategy sip")


if __name__ == "__main__":
    main()


