from datetime import date, timedelta, datetime
import requests

def bootstrap_session(product_code="FBAE41126E"):
    """
    获取民生接口请求头和cookie
    :param product_code: 产品代码，默认为FBAE41126E
    """
    response = requests.get(f"https://www.cmbcwm.com.cn/grlc/cpxq/index.htm?code={product_code}",timeout=10)
    return response.cookies

def _normalize_nav_record(raw_record):
    """
    将API原始数据转换为系统统一格式
    :param raw_record: API返回的原始记录
    :return: 标准化后的记录
    """
    # 日期格式：YYYYMMDD -> YYYY-MM-DD
    raw_date = str(raw_record['ISS_DATE'])
    normalized_date = datetime.strptime(raw_date, '%Y%m%d').strftime('%Y-%m-%d')
    
    return {
        'ISS_DATE': normalized_date,
        'NAV': str(raw_record['NAV']),
        'TOT_NAV': str(raw_record['TOT_NAV']),
        'INCOME': str(raw_record['INCOME']),
        'WEEK_CLIENTRATE': str(raw_record['WEEK_CLIENTRATE']),
    }

def query_latest_nav(cookies, product_code, query_date, retry_num):
    """
    获取民生理财产品的每日净值
    :param cookies: 会话cookies
    :param product_code: 产品代码
    :param query_date: 查询日期
    :param retry_num: 重试次数
    """
    headers = {
        "Content-Type": "application/x-www-form-urlencoded",
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Origin": "https://www.cmbcwm.com.cn",
        "Referer": "https://www.cmbcwm.com.cn"
    }
    try:
        response = requests.post("https://www.cmbcwm.com.cn/gw/po_web/BTADailyQry", data={
            "chart_type": "1",
            "real_prd_code": product_code,
            "begin_date": query_date.strftime("%Y%m%d"),
            "end_date": query_date.strftime("%Y%m%d")
        }, headers=headers, cookies=cookies,timeout=10).json()
    except Exception as e:
        print(f"获取最新净值失败:{e}")
        raise e
    navs = []
    if response.get("list") == [] and retry_num < 15:
        retry_num += 1
        print(f"当前日期{query_date}无最新净值,重试{retry_num}次")
        navs = query_latest_nav(cookies, product_code, query_date - timedelta(days=1), retry_num)
    else:
        # 标准化原始数据为系统统一格式
        raw_list = response.get("list") or []
        navs = [_normalize_nav_record(r) for r in raw_list]
    return navs

if __name__ == "__main__":
    product_code = "FBAE41126E"
    cookies = bootstrap_session(product_code)
    navs = query_latest_nav(cookies, product_code, date.today(), 0)
    print(f"最新净值获取完成:{navs}")