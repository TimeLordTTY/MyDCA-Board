from datetime import date, timedelta, datetime
import requests

def _normalize_nav_record(raw_record, product_code):
    """
    将API原始数据转换为系统统一格式
    :param raw_record: API返回的原始记录
    :param product_code: 产品代码
    :return: 标准化后的记录（包含必需字段）
    """
    # 日期格式：YYYYMMDD -> YYYY-MM-DD
    raw_date = str(raw_record['ISS_DATE'])
    normalized_date = datetime.strptime(raw_date, '%Y%m%d').strftime('%Y-%m-%d')
    fetched_at = datetime.now().isoformat()
    
    return {
        # 必需字段
        'PRODUCT_CODE': product_code,
        'ISS_DATE': normalized_date,
        'NAV': str(raw_record['NAV']),
        'fetched_at': fetched_at,
        # 扩展字段
        'TOT_NAV': str(raw_record['TOT_NAV']),
        'INCOME': str(raw_record['INCOME']),
        'WEEK_CLIENTRATE': str(raw_record['WEEK_CLIENTRATE']),
    }

def query_latest_nav(product_code, query_date, retry_num):
    """
    获取民生理财产品的每日净值
    :param product_code: 产品代码
    :param query_date: 查询日期
    :param retry_num: 重试次数
    :return: List[Dict] 净值记录列表（包含0或1条），失败返回空列表[]
    """
    headers = {
        "Content-Type": "application/x-www-form-urlencoded",
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Origin": "https://www.cmbcwm.com.cn",
        "Referer": "https://www.cmbcwm.com.cn"
    }
    try:
        print(f"开始获取基金 {product_code} {query_date} 的净值")
        response = requests.post("https://www.cmbcwm.com.cn/gw/po_web/BTADailyQry", data={
            "chart_type": "1",
            "real_prd_code": product_code,
            "begin_date": query_date.strftime("%Y%m%d"),
            "end_date": query_date.strftime("%Y%m%d")
        }, headers=headers, timeout=10).json()
    except Exception as e:
        print(f"获取最新净值失败:{e}")
        raise e
    raw_list = response.get("list") or []
    
    if not raw_list and retry_num < 15:
        retry_num += 1
        print(f"当前日期 {query_date} 无净值数据，回溯到前一天（重试 {retry_num} 次）")
        return query_latest_nav(product_code, query_date - timedelta(days=1), retry_num)
    
    if not raw_list:
        print(f"已重试 {retry_num} 次，仍无数据")
        return []  # 返回空列表
    
    # 标准化原始数据为系统统一格式（只取第一条，返回列表）
    print(f"产品 {product_code} 净值获取成功，原始数据: {raw_list[0]}")
    normalized = _normalize_nav_record(raw_list[0], product_code)
    return [normalized]  # 返回单元素列表

if __name__ == "__main__":
    product_code = "FBAE41126E"
    # cookies = bootstrap_session(product_code)
    navs = query_latest_nav(product_code, date.today(), 0)
    print(f"最新净值获取完成:{navs}")