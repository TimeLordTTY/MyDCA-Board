"""
基金净值采集脚本
使用akshare采集基金净值数据，写入nav表
"""
import sys
import os
import io

# 设置标准输出编码为UTF-8（Windows兼容）
if sys.platform == 'win32':
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
    sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8', errors='replace')
import pymysql
import pandas as pd
from datetime import datetime, date
from typing import List, Optional
import akshare as ak
import time

# 添加父目录到路径
sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from market.config import DB_CONFIG, COLLECT_CONFIG


class FundCollector:
    """基金净值采集器"""
    
    def __init__(self):
        self.conn = None
        self.connect_db()
    
    def connect_db(self):
        """连接数据库"""
        try:
            self.conn = pymysql.connect(**DB_CONFIG)
            print("数据库连接成功")
        except Exception as e:
            print(f"数据库连接失败: {e}")
            raise
    
    def close_db(self):
        """关闭数据库连接"""
        if self.conn:
            self.conn.close()
    
    def get_active_funds(self) -> List[dict]:
        """获取所有启用的基金产品"""
        with self.conn.cursor(pymysql.cursors.DictCursor) as cursor:
            sql = """
                SELECT id, product_code, product_name, asset_type, data_source
                FROM product_master
                WHERE is_active = 1
                AND asset_type IN ('FUND', 'MMF', 'LOF')
                AND channel = 'OTC'
            """
            cursor.execute(sql)
            return cursor.fetchall()
    
    def fetch_fund_nav(self, product_code: str, asset_type: str) -> Optional[dict]:
        """
        采集基金净值
        
        Args:
            product_code: 产品代码
            asset_type: 资产类型
            
        Returns:
            包含nav_date和nav的字典，如果采集失败返回None
        """
        try:
            # 使用akshare采集基金净值
            # 注意：akshare的接口可能会变化，需要根据实际情况调整
            if asset_type == 'MMF':
                # 货币基金使用不同的接口
                df = ak.fund_em_money_fund_info(fund=product_code)
            else:
                # 普通基金使用基金净值接口
                df = ak.fund_em_open_fund_info(fund=product_code)
            
            if df is None or df.empty:
                print(f"未获取到产品 {product_code} 的净值数据")
                return None
            
            # 获取最新净值（最后一行）
            latest = df.iloc[-1]
            nav_date = pd.to_datetime(latest.get('净值日期', latest.get('日期', date.today())))
            nav = float(latest.get('单位净值', latest.get('净值', 0)))
            
            return {
                'nav_date': nav_date.date() if hasattr(nav_date, 'date') else nav_date,
                'nav': nav
            }
        except Exception as e:
            print(f"采集产品 {product_code} 净值失败: {e}")
            return None
    
    def save_nav(self, product_id: int, nav_date: date, nav: float):
        """保存净值到数据库"""
        with self.conn.cursor() as cursor:
            # 检查是否已存在
            check_sql = """
                SELECT id FROM nav
                WHERE product_id = %s AND nav_date = %s
            """
            cursor.execute(check_sql, (product_id, nav_date))
            if cursor.fetchone():
                # 更新
                update_sql = """
                    UPDATE nav
                    SET nav = %s, updated_at = NOW()
                    WHERE product_id = %s AND nav_date = %s
                """
                cursor.execute(update_sql, (nav, product_id, nav_date))
            else:
                # 插入
                insert_sql = """
                    INSERT INTO nav (product_id, nav_date, nav, created_at, updated_at)
                    VALUES (%s, %s, %s, NOW(), NOW())
                """
                cursor.execute(insert_sql, (product_id, nav_date, nav))
            self.conn.commit()
    
    def collect_all(self):
        """采集所有基金的净值"""
        funds = self.get_active_funds()
        print(f"开始采集 {len(funds)} 个基金的净值...")
        
        success_count = 0
        fail_count = 0
        
        for fund in funds:
            product_id = fund['id']
            product_code = fund['product_code']
            product_name = fund['product_name']
            asset_type = fund['asset_type']
            
            print(f"正在采集: {product_name} ({product_code})...")
            
            nav_data = self.fetch_fund_nav(product_code, asset_type)
            if nav_data:
                self.save_nav(product_id, nav_data['nav_date'], nav_data['nav'])
                print(f"  ✓ 成功: {nav_data['nav_date']} = {nav_data['nav']}")
                success_count += 1
            else:
                print(f"  ✗ 失败")
                fail_count += 1
            
            # 避免请求过快
            time.sleep(1)
        
        print(f"\n采集完成: 成功 {success_count}, 失败 {fail_count}")
    
    def run(self):
        """运行采集任务"""
        try:
            self.collect_all()
        finally:
            self.close_db()


if __name__ == '__main__':
    collector = FundCollector()
    collector.run()
