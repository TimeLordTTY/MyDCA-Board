# 财富中枢系统 - Mobile端应用

## 项目信息

- **技术栈**: Vue 3 + TypeScript + Vant 4
- **构建工具**: Vite
- **状态管理**: Pinia
- **路由**: Vue Router

## 设计理念

### 与PC端保持一致
- ✅ 淡蓝色主题（#4ea4ff）
- ✅ 卡片式布局
- ✅ 圆角设计
- ✅ 中国股市习惯：红涨绿跌

### 现代移动端设计
- ✅ 底部Tab导航（5个Tab）
- ✅ 下拉刷新（PullRefresh）
- ✅ 手势交互支持
- ✅ 流畅的页面切换动画
- ✅ 触摸反馈效果
- ✅ 安全区域适配（刘海屏）
- ✅ 毛玻璃效果（backdrop-filter）

## 项目结构

```
mobile-app/
├── src/
│   ├── views/          # 页面组件
│   │   ├── Login.vue           # 登录页面
│   │   ├── Dashboard.vue       # 看板首页
│   │   ├── QuickEntry.vue      # 快速录入
│   │   ├── Settlements.vue     # 待结算确认
│   │   ├── Holdings.vue         # 持仓查看
│   │   └── Settings.vue        # 设置页面
│   ├── layouts/        # 布局组件
│   │   └── MainLayout.vue      # 主布局（包含Tab导航）
│   ├── router/         # 路由配置
│   ├── styles/         # 样式文件
│   └── main.ts         # 入口文件
├── package.json
├── vite.config.ts
└── tsconfig.json
```

## 开发

### 安装依赖

```bash
cd web/mobile-app
npm install
```

### 启动开发服务器

```bash
npm run dev
```

访问：http://localhost:3001

### 构建

```bash
npm run build
```

## 功能特性

### 已实现功能

1. **登录/注册**
   - 移动端优化的登录界面
   - 底部弹窗注册
   - JWT认证

2. **看板首页**
   - 资产概览KPI卡片（4个）
   - 资产配置预览
   - 待结算清单（最多3条）
   - 核心持仓Top 5
   - 下拉刷新

3. **快速录入**
   - 消费/收入快速录入
   - 统一记账入口（支持所有业务类型）
   - 最近记录查看

4. **待结算确认**
   - 待结算订单列表
   - 结算确认表单
   - 日期选择器

5. **持仓查看**
   - 持仓列表
   - 持仓详情弹窗
   - 浮动盈亏显示

6. **设置页面**
   - 用户信息展示
   - 功能菜单
   - 退出登录

## 注意事项

1. **共享层依赖**：必须先编译 `web/shared`，再编译 `mobile-app`
2. **API对接**：所有API调用使用 `@wealth-hub/shared` 中的API Client
3. **类型定义**：所有类型定义使用 `@wealth-hub/shared` 中的类型
4. **样式系统**：保持与PC端一致的CSS变量和设计风格
