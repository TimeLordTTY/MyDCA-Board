#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
测试所有Python脚本
用于验证数据采集、指标计算和定时任务功能
"""

import sys
import os
import io

# 设置标准输出编码为UTF-8（Windows兼容）
if sys.platform == 'win32':
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
    sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8', errors='replace')

# 添加脚本目录到路径
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

def test_market_collectors():
    """测试行情数据采集"""
    print("=" * 60)
    print("测试行情数据采集")
    print("=" * 60)
    
    try:
        # 测试基金净值采集器类
        print("\n1. 测试基金净值采集...")
        from market.fund_collector import FundCollector
        print("   [OK] fund_collector 模块导入成功")
        
        # 测试ETF行情采集器类
        print("\n2. 测试ETF行情采集...")
        from market.etf_collector import ETFCollector
        print("   [OK] etf_collector 模块导入成功")
        
        # 测试配置文件
        print("\n3. 测试配置文件...")
        from market.config import DB_CONFIG
        print("   [OK] config 模块导入成功")
        
        print("\n[OK] 行情数据采集模块测试通过")
        return True
    except ImportError as e:
        print(f"\n[FAIL] 导入失败: {e}")
        print("  请先安装依赖: pip install -r scripts/market/requirements.txt")
        return False
    except Exception as e:
        print(f"\n[FAIL] 测试失败: {e}")
        return False

def test_indicator_calculators():
    """测试指标计算"""
    print("\n" + "=" * 60)
    print("测试指标计算")
    print("=" * 60)
    
    try:
        # 测试MA计算器类
        print("\n1. 测试MA计算器...")
        from indicator.ma_calculator import MACalculator
        print("   [OK] ma_calculator 模块导入成功")
        
        # 测试MACD计算器类
        print("\n2. 测试MACD计算器...")
        from indicator.macd_calculator import MACDCalculator
        print("   [OK] macd_calculator 模块导入成功")
        
        # 测试RSI计算器类
        print("\n3. 测试RSI计算器...")
        from indicator.rsi_calculator import RSICalculator
        print("   [OK] rsi_calculator 模块导入成功")
        
        # 测试主计算器
        print("\n4. 测试主计算器...")
        from indicator.calculator import IndicatorCalculator
        print("   [OK] calculator 模块导入成功")
        
        # 测试配置文件
        print("\n5. 测试配置文件...")
        from indicator.config import DB_CONFIG
        print("   [OK] config 模块导入成功")
        
        print("\n[OK] 指标计算模块测试通过")
        return True
    except ImportError as e:
        print(f"\n[FAIL] 导入失败: {e}")
        print("  请先安装依赖: pip install -r scripts/indicator/requirements.txt")
        return False
    except Exception as e:
        print(f"\n[FAIL] 测试失败: {e}")
        return False

def test_scheduler():
    """测试定时任务调度"""
    print("\n" + "=" * 60)
    print("测试定时任务调度")
    print("=" * 60)
    
    try:
        from scheduler.scheduler import TaskScheduler
        print("   [OK] scheduler 模块导入成功")
        print("\n[OK] 定时任务调度模块测试通过")
        return True
    except ImportError as e:
        print(f"\n[FAIL] 导入失败: {e}")
        print("  请先安装依赖: pip install -r scripts/scheduler/requirements.txt")
        import traceback
        traceback.print_exc()
        return False
    except Exception as e:
        print(f"\n[FAIL] 测试失败: {e}")
        import traceback
        traceback.print_exc()
        return False

def main():
    """主测试函数"""
    print("\n" + "=" * 60)
    print("Python脚本测试")
    print("=" * 60)
    
    results = []
    
    # 测试行情数据采集
    results.append(("行情数据采集", test_market_collectors()))
    
    # 测试指标计算
    results.append(("指标计算", test_indicator_calculators()))
    
    # 测试定时任务调度
    results.append(("定时任务调度", test_scheduler()))
    
    # 汇总结果
    print("\n" + "=" * 60)
    print("测试结果汇总")
    print("=" * 60)
    
    for name, success in results:
        status = "[OK] 通过" if success else "[FAIL] 失败"
        print(f"{name}: {status}")
    
    all_passed = all(result[1] for result in results)
    
    if all_passed:
        print("\n[OK] 所有测试通过！")
        return 0
    else:
        print("\n[FAIL] 部分测试失败，请检查依赖安装和配置")
        return 1

if __name__ == '__main__':
    sys.exit(main())
