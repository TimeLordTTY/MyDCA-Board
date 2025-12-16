from datetime import date, timedelta, datetime
import requests
import re

def _parse_html_table(html_content):
    """
    解析东方财富返回的HTML表格
    :param html_content: HTML表格内容
    :return: 解析后的净值数据字典
    """
    # 提取表格行 <tr>...</tr>
    tr_pattern = r'<tr>(.*?)</tr>'
    rows = re.findall(tr_pattern, html_content, re.DOTALL)
    
    if len(rows) < 2:  # 至少需要表头和一行数据
        return None
    
    # 解析数据行（跳过表头）
    data_row = rows[1] if len(rows) > 1 else None
    if not data_row:
        return None
    
    # 提取单元格 <td>...</td>
    td_pattern = r'<td[^>]*>(.*?)</td>'
    cells = re.findall(td_pattern, data_row)
    
    if len(cells) < 3:
        return None
    
    # 字段顺序：净值日期、单位净值、累计净值、日增长率、申购状态、赎回状态、分红送配
    return {
        'jzrq': cells[0].strip(),           # 净值日期 YYYY-MM-DD
        'dwjz': cells[1].strip(),           # 单位净值
        'ljjz': cells[2].strip(),           # 累计净值
        'jzzzl': cells[3].strip() if len(cells) > 3 else '0',  # 日增长率
    }

def _normalize_nav_record(raw_record, product_code, query_date):
    """
    将东方财富API原始数据转换为系统统一格式
    :param raw_record: 东方财富API返回的历史净值数据
    :param product_code: 产品代码
    :param query_date: 查询日期
    :return: 标准化后的记录（包含必需字段）
    """
    # 东方财富API返回字段：
    # jzrq: 净值日期 (YYYY-MM-DD)
    # dwjz: 单位净值
    # ljjz: 累计净值
    # jzzzl: 日增长率
    
    fetched_at = datetime.now().isoformat()
    
    return {
        # 必需字段
        'PRODUCT_CODE': product_code,
        'ISS_DATE': raw_record.get('jzrq', query_date.strftime('%Y-%m-%d')),
        'NAV': str(raw_record.get('dwjz', '0')),
        'fetched_at': fetched_at,
        # 扩展字段
        'TOT_NAV': str(raw_record.get('ljjz', raw_record.get('dwjz', '0'))),
        'INCOME': '0',
        'WEEK_CLIENTRATE': str(raw_record.get('jzzzl', '0')),
    }

def query_latest_nav(product_code, query_date, retry_num):
    """
    从东方财富基金网获取产品的最新净值
    :param product_code: 产品代码（基金代码）
    :param query_date: 查询日期
    :param retry_num: 重试次数
    :return: List[Dict] 净值记录列表（包含0或1条），失败返回空列表[]
    """
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Referer": "http://fund.eastmoney.com/"
    }
    try:
        print(f"开始获取基金 {product_code} 的净值（查询日期: {query_date}）")
        # 获取最新一页的第一条记录
        url = f"http://fund.eastmoney.com/f10/F10DataApi.aspx?type=lsjz&code={product_code}&page=1&per=1"
        response = requests.get(url, headers=headers, timeout=10)
        
        # 检查响应状态
        if response.status_code != 200:
            raise Exception(f"HTTP {response.status_code}: {response.text[:200]}")
        
        # 响应内容是 JavaScript 格式：var apidata={ content:"<table>...</table>", ... }
        html_text = response.text
        
        # 提取 content 中的HTML内容
        content_match = re.search(r'content:"(.*?)",records:', html_text, re.DOTALL)
        if not content_match:
            raise Exception(f"无法解析响应格式: {html_text[:200]}")
        
        html_content = content_match.group(1)
        # 处理转义字符
        html_content = html_content.replace(r'<\/td>', '</td>').replace(r'<\/tr>', '</tr>')
        
        print(f"收到HTML内容: {html_content[:300]}")
        
        # 解析HTML表格
        fund_data = _parse_html_table(html_content)
        
        if not fund_data:
            raise Exception("无法从HTML表格中提取净值数据")
        
    except Exception as e:
        print(f"获取基金净值失败: {e}")
        if retry_num < 3:  # 最多重试3次
            retry_num += 1
            print(f"重试第 {retry_num} 次...")
            return query_latest_nav(product_code, query_date - timedelta(days=1), retry_num)
        raise e
    
    # 检查是否有有效数据
    if not fund_data.get('dwjz') or fund_data.get('dwjz') == '---':
        if retry_num < 15:
            retry_num += 1
            print(f"无有效净值数据，回溯查询（重试 {retry_num} 次）")
            return query_latest_nav(product_code, query_date - timedelta(days=1), retry_num)
        else:
            print(f"已重试 {retry_num} 次，仍无数据")
            return []  # 返回空列表
    
    # 标准化数据（返回单元素列表）
    print(f"基金 {product_code} 净值获取成功，原始数据: {fund_data}")
    nav_record = _normalize_nav_record(fund_data, product_code, query_date)
    return [nav_record]  # 返回单元素列表

if __name__ == "__main__":
    # 测试：兴全合润混合
    product_code = "163406"
    navs = query_latest_nav(product_code, date.today(), 0)
    print(f"最新净值获取完成: {navs}")