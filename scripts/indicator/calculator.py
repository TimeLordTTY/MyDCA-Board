"""
指标计算主程序
计算所有产品的技术指标
"""
import sys
import os
import pymysql
import pandas as pd
from datetime import date, datetime, timedelta
from typing import List
import numpy as np

sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from market.config import DB_CONFIG

# 导入各个指标计算器
from ma_calculator import MACalculator
from macd_calculator import MACDCalculator
from rsi_calculator import RSICalculator


class IndicatorCalculator:
    """指标计算器主类"""
    
    def __init__(self):
        self.conn = None
        self.connect_db()
        self.ma_calc = MACalculator(self.conn)
        self.macd_calc = MACDCalculator(self.conn)
        self.rsi_calc = RSICalculator(self.conn)
    
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
    
    def get_active_products(self) -> List[dict]:
        """获取所有启用的产品"""
        with self.conn.cursor(pymysql.cursors.DictCursor) as cursor:
            sql = """
                SELECT id, product_code, product_name, asset_type
                FROM product_master
                WHERE is_active = 1
            """
            cursor.execute(sql)
            return cursor.fetchall()
    
    def calculate_all_indicators(self, product_id: int, end_date: date = None):
        """计算产品的所有指标"""
        if end_date is None:
            end_date = date.today()
        
        print(f"计算产品 {product_id} 的指标（截止日期: {end_date}）...")
        
        # 计算MA
        self.ma_calc.calculate(product_id, end_date)
        
        # 计算MACD
        self.macd_calc.calculate(product_id, end_date)
        
        # 计算RSI
        self.rsi_calc.calculate(product_id, end_date)
    
    def calculate_all_products(self, end_date: date = None):
        """计算所有产品的指标"""
        products = self.get_active_products()
        print(f"开始计算 {len(products)} 个产品的指标...")
        
        for product in products:
            try:
                self.calculate_all_indicators(product['id'], end_date)
            except Exception as e:
                print(f"计算产品 {product['product_name']} 指标失败: {e}")
        
        print("\n所有指标计算完成")
    
    def run(self, product_id: int = None, end_date: date = None):
        """
        运行指标计算
        
        Args:
            product_id: 产品ID，如果为None则计算所有产品
            end_date: 截止日期，如果为None则使用今天
        """
        try:
            if end_date is None:
                end_date = date.today()
            
            if product_id:
                self.calculate_all_indicators(product_id, end_date)
            else:
                self.calculate_all_products(end_date)
        finally:
            self.close_db()


if __name__ == '__main__':
    import argparse
    parser = argparse.ArgumentParser(description='指标计算')
    parser.add_argument('--product-id', type=int, help='产品ID，如果不指定则计算所有产品')
    parser.add_argument('--date', help='截止日期（格式：YYYY-MM-DD）')
    args = parser.parse_args()
    
    end_date = None
    if args.date:
        end_date = datetime.strptime(args.date, '%Y-%m-%d').date()
    
    calculator = IndicatorCalculator()
    calculator.run(args.product_id, end_date)
