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
    
    fetched_at = datetime.now().strftime('%Y-%m-%d %H:%M:%S.%f')[:23]  # 毫秒精度
    
    return {
        # 必需字段（统一小写命名）
        'product_code': product_code,
        'nav_date': raw_record.get('jzrq', query_date.strftime('%Y-%m-%d')),
        'nav': str(raw_record.get('dwjz', '0')),
        'fetched_at': fetched_at,
        # 扩展字段
        'total_nav': str(raw_record.get('ljjz', raw_record.get('dwjz', '0'))),
        'income': '0',
        'weekly_rate': str(raw_record.get('jzzzl', '0')),
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

def query_nav_history(product_code, start_date, end_date, page_size=20):
    """
    查询指定日期范围内的历史净值
    :param product_code: 产品代码（基金代码）
    :param start_date: 开始日期 (date 或 str 'YYYY-MM-DD')
    :param end_date: 结束日期 (date 或 str 'YYYY-MM-DD')
    :param page_size: 每页记录数（默认20）
    :return: List[Dict] 净值记录列表，按日期升序排列
    """
    # 日期格式化
    if isinstance(start_date, str):
        sdate = start_date
    else:
        sdate = start_date.strftime('%Y-%m-%d')
    
    if isinstance(end_date, str):
        edate = end_date
    else:
        edate = end_date.strftime('%Y-%m-%d')
    
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Referer": "http://fund.eastmoney.com/"
    }
    
    all_records = []
    page = 1
    
    while True:
        try:
            # 东方财富历史净值 API，支持日期范围
            url = f"http://fund.eastmoney.com/f10/F10DataApi.aspx?type=lsjz&code={product_code}&page={page}&per={page_size}&sdate={sdate}&edate={edate}"
            response = requests.get(url, headers=headers, timeout=10)
            
            if response.status_code != 200:
                raise Exception(f"HTTP {response.status_code}")
            
            html_text = response.text
            
            # 提取总记录数
            records_match = re.search(r'records:(\d+)', html_text)
            total_records = int(records_match.group(1)) if records_match else 0
            
            if total_records == 0:
                break
            
            # 提取 content 中的HTML内容
            content_match = re.search(r'content:"(.*?)",records:', html_text, re.DOTALL)
            if not content_match:
                break
            
            html_content = content_match.group(1)
            html_content = html_content.replace(r'<\/td>', '</td>').replace(r'<\/tr>', '</tr>')
            
            # 解析所有数据行
            records = _parse_html_table_all(html_content)
            if not records:
                break
            
            for record in records:
                nav_record = _normalize_nav_record(record, product_code, date.today())
                all_records.append(nav_record)
            
            # 检查是否还有更多页
            if page * page_size >= total_records:
                break
            
            page += 1
            
        except Exception as e:
            print(f"获取历史净值失败 (page={page}): {e}")
            break
    
    # 按日期升序排列
    all_records.sort(key=lambda x: x['nav_date'])
    return all_records


def _parse_html_table_all(html_content):
    """
    解析东方财富返回的HTML表格，返回所有数据行
    :param html_content: HTML表格内容
    :return: 解析后的净值数据列表
    """
    # 提取tbody中的所有数据行
    tbody_match = re.search(r'<tbody>(.*?)</tbody>', html_content, re.DOTALL)
    if not tbody_match:
        # 没有 tbody，直接解析所有 tr
        tr_pattern = r'<tr>(.*?)</tr>'
        rows = re.findall(tr_pattern, html_content, re.DOTALL)
        rows = rows[1:] if rows else []  # 跳过表头
    else:
        tbody_content = tbody_match.group(1)
        tr_pattern = r'<tr>(.*?)</tr>'
        rows = re.findall(tr_pattern, tbody_content, re.DOTALL)
    
    records = []
    for row in rows:
        # 提取单元格 <td>...</td>
        td_pattern = r'<td[^>]*>(.*?)</td>'
        cells = re.findall(td_pattern, row)
        
        if len(cells) >= 3:
            records.append({
                'jzrq': cells[0].strip(),
                'dwjz': cells[1].strip(),
                'ljjz': cells[2].strip(),
                'jzzzl': cells[3].strip() if len(cells) > 3 else '0',
            })
    
    return records


if __name__ == "__main__":
    # 测试：兴全合润混合
    product_code = "161130"
    navs = query_latest_nav(product_code, date.today(), 0)
    print(f"最新净值获取完成: {navs}")