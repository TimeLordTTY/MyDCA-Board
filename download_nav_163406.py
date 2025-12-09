import requests
import csv
import time
import json

def fetch_fund_nav(fund_code: str,
                   start_date: str = "",
                   end_date: str = "",
                   page_size: int = 20) -> list:
    """
    从天天基金(东财)拉取某只基金的历史净值数据

    :param fund_code: 基金代码，比如 '163406'
    :param start_date: 起始日期 'YYYY-MM-DD'，留空表示最早开始
    :param end_date: 结束日期 'YYYY-MM-DD'，留空表示最新
    :param page_size: 每页条数，API 实际最多返回 20 条
    :return: 列表，每个元素是 dict: {
                'date': 日期,
                'nav': 单位净值,
                'accum_nav': 累计净值,
                'change_pct': 涨跌幅（%）
            }
    """

    url = "https://api.fund.eastmoney.com/f10/lsjz"
    headers = {
        # 模拟浏览器，否则有概率被拦
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)",
        # 必须加 Referer，否则接口可能返回异常数据
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
        resp = requests.get(url, headers=headers, params=params, timeout=10)
        resp.raise_for_status()

        data = resp.json()
        # 天天基金结构一般是：{"Data":{"LSJZList":[...], "TotalCount": xxx}, "ErrCode":0, ...}
        data_obj = data.get("Data", {})
        # 有时候 Data 返回的是 JSON 字符串而非对象，需要再解析一次
        if isinstance(data_obj, str):
            data_obj = json.loads(data_obj) if data_obj else {}
        nav_list = data_obj.get("LSJZList", [])

        if not nav_list:
            break

        for item in nav_list:
            date = item.get("FSRQ")           # 日期
            nav = item.get("DWJZ")           # 单位净值
            accum_nav = item.get("LJJZ")     # 累计净值
            change_pct = item.get("JZZZL")   # 日涨跌幅（百分比字符串）

            if not date or not nav:
                continue

            all_rows.append({
                "date": date,
                "nav": nav,
                "accum_nav": accum_nav,
                "change_pct": change_pct
            })

        # 看是否还有下一页：如果返回条数少于请求的 pageSize，说明已经是最后一页
        if len(nav_list) < page_size:
            break

        params["pageIndex"] += 1
        print(f"  已获取第 {params['pageIndex'] - 1} 页，累计 {len(all_rows)} 条...")
        # 礼貌一点，别疯狂打接口
        time.sleep(0.3)

    # 天天基金返回的一般是按日期倒序，这里给你反转成从旧到新
    all_rows.reverse()
    return all_rows


def save_to_csv(rows: list, filename: str):
    """
    把净值数据保存为 CSV
    :param rows: fetch_fund_nav 返回的列表
    :param filename: 输出文件名
    """
    fieldnames = ["date", "nav", "accum_nav", "change_pct"]
    with open(filename, "w", newline="", encoding="utf-8-sig") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        for r in rows:
            writer.writerow(r)


if __name__ == "__main__":
    # 🟣 这里改基金代码就行
    fund_code = "163406"  # 兴全合润混合(LOF)
    # 可以改时间范围，不改就是全历史
    start_date = "2010-01-01"       # 比如 "2010-01-01"
    end_date = "2025-12-08"         # 比如 "2025-12-31"

    print(f"正在下载基金 {fund_code} 的历史净值数据……")
    rows = fetch_fund_nav(fund_code, start_date, end_date)

    print(f"共获取 {len(rows)} 条记录")
    out_file = f"fund_{fund_code}_nav.csv"
    save_to_csv(rows, out_file)
    print(f"已保存到 {out_file}")