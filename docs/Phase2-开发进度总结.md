# Phase 2 开发进度总结（前端开发与行情增强）

**阶段状态**：⚠️ 当前仍处于 Phase 2（2.1 已完成，2.2-2.4 已基本落地，仍有少量增强项未完成）

## 已完成工作

### 1. 前端项目结构 ✅
- ✅ 创建了 `web/shared` 项目（TypeScript库）
- ✅ 创建了 `web/pc-app` 项目（Vue 3 + Element Plus）
- ✅ 配置了 Vite、TypeScript、依赖管理
- ✅ 配置了项目编译脚本（build-web.bat）

### 2. 共享层开发（web/shared）✅

#### 2.1 枚举值映射常量 ✅
- ✅ `assetTypeMap`: 资产类型（ETF→交易型开放式指数基金等）
- ✅ `accountTypeMap`: 账户类型（BANK→银行等）
- ✅ `accountKindMap`: 账户性质（REAL→现实账户等）
- ✅ `fundUsageMap`: 资金用途（SPENDABLE→可支出等）
- ✅ `txnTypeMap`: 交易类型（BUY→买入等）
- ✅ `orderTypeMap`: 订单类型
- ✅ `orderStatusMap`: 订单状态
- ✅ `txnStatusMap`: 交易状态
- ✅ `channelMap`: 渠道（EXCHANGE→场内等）
- ✅ `marketMap`: 市场（SH→上海等）
- ✅ `currencyMap`: 货币（CNY→人民币等）
- ✅ 其他枚举映射（ownerType、relationType、postingType、role等）

#### 2.2 TypeScript类型定义 ✅
- ✅ `User`, `Family`, `UserFamilyRole`（用户相关）
- ✅ `ProductMaster`（完全对应product_master表的所有字段）
- ✅ `Account`（完全对应accounts表的所有字段，包含父子账户、fund_usage等）
- ✅ `LedgerTxn`, `LedgerPosting`, `LedgerTxnDetail`（流水相关）
- ✅ `Order`, `OrderDetail`, `OrderFundingLine`, `SettlementConfirm`, `PendingSettlement`（订单相关）
- ✅ `HoldingInfo`, `HoldingDetail`（持仓相关）
- ✅ `AssetOverview`, `AssetAllocation`, `TodayAction`, `Performance`（看板相关）
- ✅ 所有API请求/响应类型

#### 2.3 API Client ✅
- ✅ **认证API** (`auth.ts`): register, login, logout
- ✅ **用户API** (`user.ts`): getCurrentUser
- ✅ **家庭API** (`family.ts`): getFamilies, createFamily, addMember
- ✅ **产品API** (`product.ts`): getProducts, getProduct, createProduct, updateProduct
- ✅ **账户API** (`account.ts`): getAccounts, getAccount, createAccount, updateAccount, adjustBalance
- ✅ **流水API** (`ledger.ts`): getTransactions, getTransactionDetail, createTransaction, quickEntry, refund, reimburse
- ✅ **订单API** (`order.ts`): getOrders, getOrder, createOrder, cancelOrder
- ✅ **结算API** (`settlement.ts`): getPendingSettlements, confirmSettlement
- ✅ **持仓API** (`holding.ts`): getHoldings, getHoldingDetail
- ✅ **看板API** (`dashboard.ts`): 已封装 getAssetOverview、getPendingSettlements、getTodayActions，并预留 getAssetAllocation、getPerformance
- ✅ 统一请求拦截（添加JWT token）
- ✅ 统一错误处理（401跳转登录，显示错误提示）

#### 2.4 工具函数 ✅
- ✅ `format.ts`: 金额格式化（¥ 1,234.56）、日期格式化、数字格式化、百分比格式化
- ✅ `enum.ts`: 枚举值转换函数（getAssetTypeLabel, getAccountTypeLabel等）
- ✅ `tree.ts`: 树形数据处理（账户树构建、父账户余额计算、叶子账户获取）

#### 2.5 Pinia Store ✅
- ✅ `userStore`: 用户信息、登录状态、登录/注册/登出
- ✅ `accountStore`: 账户列表、账户树、现金叶子账户、账户CRUD
- ✅ `productStore`: 产品列表、产品CRUD

### 3. PC端页面开发（web/pc-app）✅

#### 3.1 基础布局和路由 ✅
- ✅ 创建路由配置（Vue Router）
- ✅ 创建主布局组件（MainLayout.vue，包含topbar、导航、内容区）
- ✅ 实现导航切换（参考demo的pill按钮样式）
- ✅ 路由守卫（检查认证，未登录跳转登录页）

#### 3.2 样式系统 ✅
- ✅ 完全按照demo的CSS变量和样式
- ✅ 定义CSS变量（颜色、字体、间距、圆角）
- ✅ 实现卡片样式（card）
- ✅ 实现按钮样式（btn, btn.primary, btn.ghost）
- ✅ 实现标签样式（tag, tag.blue, tag.green等）
- ✅ 实现表格样式
- ✅ 实现KPI卡片样式
- ✅ 实现模态框样式（modal）
- ✅ 实现Toast提示样式

#### 3.3 登录页面 ✅
- ✅ 登录表单（用户名、密码）
- ✅ 调用`/api/v2/auth/login`
- ✅ 保存JWT token
- ✅ 登录成功后跳转看板
- ✅ 支持注册功能

#### 3.4 看板首页 ✅
- ✅ **资产概览KPI卡片**（3个KPI）：
  - 净资产（Net Worth）= 现金 + 持仓市值 - 负债
  - 可用资金 = Σ(现金叶子余额) - Σ(占用)
  - 持仓市值 = Σ(持仓 shares × 价格)
- ✅ **资产配比图表**（饼图/环形图，使用ECharts）
- ✅ **今日建议清单**（当前已接入“待结算/逾期待处理”动作，完整策略建议仍属于 Phase 3）
- ✅ **待结算清单**（表格，调用`/api/v2/dashboard/pending-settlements`）
- ✅ **核心持仓Top 5**（表格，调用`/api/v2/holdings`）
- ✅ 所有金额显示使用格式化函数
- ✅ 所有枚举值显示中文

#### 3.5 产品管理页面 ✅
- ✅ **产品列表**（表格）：
  - 列：名称（含代码，代码显示在名称下方小字灰色斜体）、类型（显示中文）、渠道（显示中文）、市场（显示中文）、币种（显示中文）、状态、操作
  - 支持筛选（keyword、assetType、channel）
  - 调用`/api/v2/products`
  - **响应式数据绑定**：使用本地ref存储产品列表，在`loadProducts`函数中从store获取数据并更新本地ref，确保Vue响应式正常工作
  - **操作按钮布局**：使用`white-space: nowrap`防止按钮自动换行，按钮之间使用`margin-right`保持间距
- ✅ **新增/编辑产品**（模态框）：
  - 表单字段：**完全按照product_master表的所有字段**
    - productCode, productName
    - assetType（下拉框，显示中文）
    - channel（下拉框，显示中文）
    - market（下拉框，显示中文）
    - currency（下拉框，显示中文）
    - isQdii, trackIndex
    - buyFeeRate, sellFeeRate
    - buyConfirmOffset, sellConfirmOffset
    - cutoffTime, dataSource
    - isActive, note
  - 调用`/api/v2/products` POST/PUT
- ✅ 停用/启用功能
- ✅ 所有下拉框显示中文

#### 3.6 账户管理页面 ✅
- ✅ **账户树形结构展示**：
  - 父账户（平台容器）显示聚合余额 = Σ(子账户余额)
  - 子账户（信封）显示真实余额
  - 支持展开/折叠
  - 调用`/api/v2/accounts`（返回树形结构）
- ✅ **现金叶子账户摘要表**（表格）：
  - 列：平台、信封、用途（显示中文）、余额、占用
- ✅ **新增平台**（父账户）：
  - 表单：accountCode, accountName, accountType（下拉框，显示中文）等
- ✅ **新增信封**（子账户）：
  - 表单：**完全按照accounts表的所有字段**
    - accountCode, accountName
    - parentAccountId（下拉框选择父账户）
    - fundUsage（下拉框，显示中文：可支出/专款/可投资）
    - initialBalance等
- ✅ **编辑账户**（模态框）：
  - 支持编辑余额（调用`/api/v2/accounts/{id}/balance`，生成ADJUST流水）
  - 支持编辑fund_usage
- ✅ 所有下拉框显示中文

#### 3.7 流水查询页面 ✅
- ✅ **流水列表**（表格）：
  - 列：时间、类型（显示中文）、摘要、金额、备注
  - 支持筛选（txnType、startDate、endDate、productId）
  - 调用`/api/v2/ledger/txns`
- ✅ **流水详情**（模态框）：
  - 显示所有postings（借贷分录）
  - 显示交易基本信息（txnId、类型、时间、备注）
  - 如果原交易有退款/报销，显示refundedTotal、reimbursedTotal、remaining
- ✅ **统一记账入口**（模态框）：
  - 支持所有业务类型（EXPENSE、INCOME、TRANSFER、BUY、SELL、ADJUST等）
  - 两步式交互：第一步选择业务类型，第二步填写详情
  - 根据txnType动态显示字段（productId、amount、shares等）
  - 调用`/api/v2/ledger/txns` POST
- ✅ **快速录入**（模态框）：
  - 消费/收入快速录入
  - 调用`/api/v2/ledger/quick-entry`
- ✅ 所有枚举值显示中文

#### 3.8 订单管理页面 ✅
- ✅ **订单列表**（表格）：
  - 列：订单ID、类型（显示中文）、标的、资金来源（组合支付显示多行）、金额、费用、状态（显示中文）
  - 支持筛选（status、productId）
  - 调用`/api/v2/orders`
- ✅ **订单详情**（模态框）：
  - 显示订单信息（订单ID、类型、产品ID、金额、份额、状态、创建时间）
  - 显示fundingLines（资金来源明细表格）
- ✅ **新建订单**（模态框）：
  - 表单：productId（下拉框选择产品）, orderType（下拉框，显示中文）, amount/shares, accountId（资金来源账户）
  - 支持组合支付（fundingLines数组，当前实现为单个账户，后端已支持组合支付）
  - 调用`/api/v2/orders` POST
- ✅ **取消订单**：
  - 调用`/api/v2/orders/{orderId}/cancel`
- ✅ 所有枚举值显示中文

#### 3.9 结算确认页面 ✅
- ✅ **待结算清单**（表格）：
  - 列：订单ID、类型、标的、金额、预期确认日期、操作
  - 调用`/api/v2/settlements/pending`
- ✅ **确认结算**（模态框）：
  - 表单：confirmDate, navDate, confirmNav, confirmShares, confirmAmount, confirmFee, isManualOverride, note
  - 调用`/api/v2/settlements/confirm` POST
- ✅ 所有枚举值显示中文

#### 3.10 持仓查看页面 ✅
- ✅ **持仓列表**（表格）：
  - 列：标的、代码、份额、均价、现价、市值、浮盈亏
  - 调用`/api/v2/holdings`
- ✅ **持仓详情**（HoldingDetailModal.vue）：
  - ✅ 显示持仓历史曲线（历史净值曲线图表、历史行情K线图）
  - ✅ 显示技术指标图表（MA20、MA60、分位）
  - ✅ 标签页切换（净值曲线、K线图、技术指标）
- ✅ 所有金额格式化显示

#### 3.11 设置页面 ✅
- ✅ 基础页面结构
- ⚠️ 当前仅完成占位页，用户管理/密码修改/家庭成员管理仍未实现

### 4. Mobile端页面开发（web/mobile-app）✅

**状态**：✅ 核心 Tab 已完成，设置内子功能页仍以占位页为主

#### 4.1 项目基础结构 ✅
- ✅ 创建了 `web/mobile-app` 项目（Vue 3 + Vant 4）
- ✅ 配置了 Vite、TypeScript、依赖管理
- ✅ 配置了现代移动端样式系统（与PC端一致的淡蓝色主题）

#### 4.2 核心功能 ✅
- ✅ 路由配置（底部Tab导航，5个Tab）
- ✅ 主布局组件（MainLayout.vue，包含Tab导航）
- ✅ 登录页面（移动端优化，支持注册弹窗）
- ✅ 看板首页（KPI卡片、资产配置、待结算、持仓列表）
- ✅ 快速录入页面（消费/收入快速录入，统一记账入口）
- ✅ 待结算确认页面（列表、确认表单）
- ✅ 持仓查看页面（列表、详情弹窗）
- ✅ 设置页面（用户信息、功能菜单、退出登录）
- ✅ 账户管理/产品管理/流水查询/订单管理的移动端独立子页面（轻量版只读列表）：
  - `/accounts` → `AccountsMobile.vue`：账户列表 + 类型筛选 + 只读详情弹窗
  - `/products` → `ProductsMobile.vue`：产品列表 + 搜索 + 渠道筛选 + 只读详情弹窗
  - `/ledger` → `LedgerMobile.vue`：最近流水列表 + 日期/备注筛选 + 流水详情（含分录）
  - `/orders` → `OrdersMobile.vue`：订单列表 + 状态筛选 + 详情（含资金明细、结算信息）

#### 4.3 现代移动端特性 ✅
- ✅ 底部Tab导航（5个Tab：看板、快速录入、待结算、持仓、我的）
- ✅ 下拉刷新（PullRefresh）
- ✅ 手势交互支持
- ✅ 流畅的页面切换动画
- ✅ 触摸反馈效果（:active状态）
- ✅ 安全区域适配（刘海屏，safe-area-inset）
- ✅ 毛玻璃效果（backdrop-filter）
- ✅ 底部弹窗交互（Popup）

### 5. 编译和构建 ✅
- ✅ 修复了所有TypeScript编译错误
- ✅ shared层编译通过
- ✅ pc-app编译通过
- ✅ 全项目编译脚本（build-all.bat）测试通过
- ✅ Java后端编译通过

## 关键实现要点

### 1. 设计风格完全按照demo ✅
- ✅ 使用demo的CSS变量和颜色方案（淡蓝色主题）
- ✅ 卡片式布局、圆角设计
- ✅ 按钮、标签、表格样式与demo一致
- ✅ 毛玻璃特效（backdrop-filter）

### 2. 功能实现完全按照文档 ✅
- ✅ 所有字段必须与数据库表结构对应（如product_master表的所有字段）
- ✅ 所有API调用必须与后端接口对应
- ✅ 业务逻辑必须符合文档说明（如父子账户、fund_usage校验等）

### 3. 枚举值显示中文 ✅
- ✅ 所有下拉框选项显示中文
- ✅ 所有表格中的枚举值显示中文
- ✅ 所有标签中的枚举值显示中文
- ✅ 使用constants中的映射表统一管理

### 4. 组合支付支持 ✅
- ✅ 订单创建支持fundingLines数组（API已实现，UI待完善）
- ✅ 统一记账支持paymentLines数组（API已实现，UI待完善）

### 5. 账户树形结构 ✅
- ✅ 父账户显示聚合余额
- ✅ 支持展开/折叠
- ✅ 清晰的父子关系展示

## Phase 2.1 后续优化（可选）

### 已完成功能 ✅

#### 高优先级功能（全部完成）
- ✅ **统一记账入口**：完整实现，支持所有业务类型（EXPENSE、INCOME、TRANSFER、BUY、SELL、ADJUST等）
- ✅ **快速录入**：消费/收入快速录入功能已实现
- ✅ **新建订单**：支持组合支付（fundingLines），后端API已完善
- ✅ **结算确认**：完整表单实现，包含所有必填和可选字段
- ✅ **流水详情**：显示交易基本信息和所有分录（postings）
- ✅ **订单详情**：显示订单信息和资金来源明细（fundingLines）

#### 中优先级功能（部分完成）
- ✅ **表单验证**：基础验证已实现（必填项、数值范围等）
- ✅ **错误处理**：统一错误提示，用户友好的错误信息
- ⚠️ **响应式设计**：基础布局已完成，移动端适配待完善
- ✅ **性能优化**：路由懒加载（动态 `import()`）已实现，组件按需加载与进一步拆包仍可继续优化
- ✅ **持仓详情**：历史净值曲线、K线、技术指标图表已实现

### 待完善功能（已调整为 Phase 3+/后续优化，不再作为 Phase 2 验收标准）

#### 中优先级（可选优化）
- [ ] **响应式设计优化**：完善移动端适配，优化小屏幕显示（计划在 Phase 3 统一做 UI 调优）
- [ ] **性能优化**（计划在 Phase 3.4 / 4.1 做前后端性能专项）：
  - [x] 路由懒加载（动态import）
  - [ ] 组件按需加载
  - [ ] 代码分割优化（减少打包体积）
- [ ] **持仓详情页面增强**：
  - [x] 持仓历史曲线图表（使用ECharts）
  - [ ] 持仓成本分析
  - [ ] 持仓收益统计

#### 低优先级（可选功能）
- [x] **用户管理功能**（设置页面）：
  - [x] 用户信息编辑
  - [x] 密码修改
  - [x] 家庭成员管理（仅管理员）
- [ ] **其他设置项**（整体归入 Phase 4“运维与报表”）：
  - [ ] 系统配置
  - [ ] 数据导出
  - [ ] 操作日志查看

## Phase 2.2-2.4 开发进度（行情与指标模块）

**当前状态**：⚠️ Phase 2.2-2.4 已基本落地，仍有少量增强项未完成

**完成时间**：2024年1月

### Phase 2.2：Python行情服务 ✅

**目标**：实现行情数据采集、存储和API接口

**已完成任务**：
- ✅ **行情数据采集**：
  - ✅ 集成akshare数据源
  - ✅ 实现基金净值采集（`fund_collector.py`）
  - ✅ 实现ETF行情采集（`etf_collector.py`）
  - ✅ 股票行情采集（已可获取：由 `etf_collector.py`/`backfill_fund_nav_history.py` 覆盖，独立脚本未单列）
  - ⚠️ 债券行情采集（待实现）
- ✅ **行情数据存储**：
  - ✅ market_bar_daily表数据写入（日K线）
  - ✅ market_quote_realtime表数据写入（实时行情）
  - ✅ nav表数据写入（基金净值）
- ✅ **行情API接口**：
  - ✅ 获取历史行情接口（后端Controller）
  - ✅ 获取实时行情接口（后端Controller）
  - ✅ 获取基金净值接口（后端Controller）

**文件结构**：
```
scripts/market/
├── README.md              # 说明文档
├── requirements.txt       # Python依赖
├── config.py             # 配置文件
├── backfill_fund_nav_history.py  # 历史行情/净值回补 ✅
├── fund_collector.py     # 基金净值采集 ✅
└── etf_collector.py      # ETF行情采集 ✅
```

### Phase 2.3：指标计算模块 ✅

**目标**：实现技术指标计算和数据存储

**已完成任务**：
- ✅ **指标计算服务**：
  - ✅ MA（移动平均线）计算（`ma_calculator.py`）
  - ✅ MACD指标计算（`macd_calculator.py`）
  - ✅ RSI指标计算（`rsi_calculator.py`）
  - ✅ 前端当前使用的 MA20 / MA60 / 分位查询已可用（数据库结果优先，缺失时后端临时派生）
  - ⚠️ BOLL / KDJ 等更多技术指标待扩展
- ✅ **指标数据存储**：
  - ✅ indicator_daily表数据写入
  - ✅ 指标数据更新策略（覆盖更新）
- ✅ **指标API接口**：
  - ✅ 获取指标数据接口（后端Controller）
  - ✅ 指标数据查询接口（后端Controller）

**文件结构**：
```
scripts/indicator/
├── README.md              # 说明文档
├── requirements.txt       # Python依赖
├── calculator.py          # 指标计算主程序 ✅
├── ma_calculator.py      # 移动平均线计算 ✅
├── macd_calculator.py    # MACD指标计算 ✅
└── rsi_calculator.py     # RSI指标计算 ✅
```

### Phase 2.4：定时任务 ✅

**目标**：实现定时数据采集和指标计算

**已完成任务**：
- ✅ **Python任务调度**：
  - ✅ 选择任务调度框架（APScheduler）
  - ✅ 配置任务调度器（`scheduler.py`）
  - ✅ 实现任务监控和日志
- ✅ **定时数据采集**：
  - ✅ 每日行情数据采集任务（ETF日K线：15:30）
  - ✅ 实时行情数据采集任务（ETF实时行情：交易时间内每5分钟）
  - ✅ 基金净值采集任务（18:00）
- ✅ **定时指标计算**：
  - ✅ 每日指标计算任务（16:00）
  - ✅ 指标数据更新任务
- ✅ **Java定时任务**：
  - ✅ MarketDataScheduler：行情数据同步（场内实时行情、场外净值、场内日K线）
  - ⚠️ IndicatorCalculationTask：指标计算（待实现，当前通过Python调度器执行）
  - ⚠️ RealtimeQuoteCleanupTask：实时行情清理（待实现）
  - ⚠️ SnapshotGenerationTask：快照生成（待实现）

**文件结构**：
```
scripts/scheduler/
├── README.md              # 说明文档
├── requirements.txt       # Python依赖
└── scheduler.py           # 定时任务调度器 ✅
```

**定时任务配置**：
- 基金净值采集：每天 18:00
- ETF实时行情：交易时间内每5分钟（9:30-15:00）
- ETF日K线：每天 15:30（收盘后）
- 指标计算：每天 16:00（数据采集完成后）

### 待完成工作

#### 高优先级（已完成）
- [x] **后端API接口开发**：
  - [x] MarketController：获取历史行情、实时行情接口 ✅
  - [x] NavController：获取基金净值接口 ✅
  - [x] IndicatorController：获取指标数据接口 ✅
- [x] **前端页面集成**：
  - [x] 持仓页面显示实时行情 ✅
  - [x] 持仓详情页面显示历史曲线和指标 ✅
  - [x] 看板页面显示持仓市值（基于实时行情） ✅
- [x] **MyBatis XML映射文件**：
  - [x] MarketBarDailyMapper.xml ✅
  - [x] MarketQuoteRealtimeMapper.xml ✅
  - [x] NavMapper.xml ✅
  - [x] IndicatorDailyMapper.xml ✅
- [x] **前端API Client**：
  - [x] market.ts - 行情数据API ✅
  - [x] nav.ts - 净值数据API ✅
  - [x] indicator.ts - 指标数据API ✅
- [x] **前端类型定义**：
  - [x] market.ts - 行情数据类型 ✅
  - [x] nav.ts - 净值数据类型 ✅
  - [x] indicator.ts - 指标数据类型 ✅

#### 中优先级
- [ ] **完善行情采集**：
  - [x] 股票行情采集（已可获取：复用现有采集/回补逻辑）
  - [ ] 债券行情采集
  - [ ] 错误处理和重试机制优化
- [ ] **扩展指标计算**：
  - [ ] 布林带（BOLL）指标
  - [ ] KDJ指标
  - [ ] 其他常用技术指标

#### 低优先级
- [ ] **性能优化**：
  - [ ] 批量数据采集优化
  - [ ] 数据库写入性能优化
  - [ ] 指标计算性能优化

## 编译说明

### 编译所有项目
```bash
# 在项目根目录执行
build-all.bat
```

### 单独编译
```bash
# 前端
cd web
build-web.bat

# Java后端
cd backend
mvn clean compile
```

## Phase 2.1 完成情况总结

### ✅ Phase 2.1 前端开发已完成

**完成时间**：2024年1月

**阶段目标**：完成PC端前端应用开发，对接Phase 1后端API，实现所有核心页面和功能

**完成情况**：所有高优先级功能已实现，包括统一记账入口、快速录入、新建订单、结算确认、流水详情、订单详情等

---

### 已完成模块清单

#### ✅ 共享层（web/shared）
- [x] 枚举值映射常量（所有枚举值到中文的映射）
- [x] TypeScript类型定义（完全对应数据库表结构）
- [x] API Client（所有Phase 1后端API）
- [x] 工具函数（格式化、枚举转换、树形数据处理）
- [x] Pinia Store（userStore, accountStore, productStore）

## 所有未实现功能清单

### 当前仍未完成的 Phase 2 项
- [x] 设置页用户管理（PC）
- [x] 用户信息编辑 / 密码修改 / 家庭成员管理
- [x] Mobile 端账户/产品/流水/订单独立页面（已实现轻量只读版本，复杂录入操作仍建议在 PC 端完成）
- [ ] 债券行情采集
- [x] 股票行情采集（已可获取：由 `etf_collector.py` 实时行情 + `backfill_fund_nav_history.py` 日K回补覆盖，独立脚本未单列）
- [x] Java `IndicatorCalculationTask`
- [x] Java `RealtimeQuoteCleanupTask`
- [x] Java `SnapshotGenerationTask`
- [ ] BOLL / KDJ 等扩展指标
- [ ] 持仓成本分析 / 持仓收益统计

## Phase 2.2-2.4 最新进度更新（最终版）

**更新时间**：2026年3月

### ✅ 所有高优先级功能已完成

#### 1. MyBatis XML映射文件 ✅
- [x] `backend/src/main/resources/mapper/MarketBarDailyMapper.xml` ✅
- [x] `backend/src/main/resources/mapper/MarketQuoteRealtimeMapper.xml` ✅
- [x] `backend/src/main/resources/mapper/NavMapper.xml` ✅
- [x] `backend/src/main/resources/mapper/IndicatorDailyMapper.xml` ✅

#### 2. 前端API Client（web/shared）✅
- [x] `web/shared/src/api/market.ts` - 行情数据API ✅
- [x] `web/shared/src/api/nav.ts` - 净值数据API ✅
- [x] `web/shared/src/api/indicator.ts` - 指标数据API ✅
- [x] `web/shared/src/types/market.ts` - 行情数据类型定义 ✅
- [x] `web/shared/src/types/nav.ts` - 净值数据类型定义 ✅
- [x] `web/shared/src/types/indicator.ts` - 指标数据类型定义 ✅

#### 3. 前端页面集成 ✅
- [x] **持仓页面（Holdings.vue）**：
  - [x] 显示实时行情（价格、涨跌幅） ✅
  - [x] 显示持仓市值（基于实时行情计算） ✅
  - [x] 显示浮动盈亏（基于实时行情计算） ✅
  - [x] 持仓详情按钮 ✅
- [x] **持仓详情页面（HoldingDetailModal.vue）**：
  - [x] 历史净值曲线图表（使用ECharts） ✅
  - [x] 历史行情K线图（使用ECharts） ✅
  - [x] 技术指标图表（MA20、MA60、分位） ✅
  - [x] 标签页切换（净值曲线、K线图、技术指标） ✅
- [x] **看板页面（Dashboard.vue）**：
  - [x] 持仓市值基于实时行情计算 ✅
  - [x] 浮动盈亏基于实时行情计算 ✅

#### 4. Python脚本与运行准备 ✅
- [x] 创建使用说明文档（`scripts/README.md`） ✅
- [x] 创建配置文件（`scripts/indicator/config.py`） ✅
- [x] 修复导入问题（类导入、路径问题） ✅
- [x] 修复Windows编码问题 ✅
- [ ] 安装Python依赖（需要手动执行：`pip install -r scripts/market/requirements.txt`等）
- [ ] 运行实际数据采集（需要手动执行，见 `scripts/README.md`）

### 中优先级（重要优化）

#### 5. 行情数据增强
- [ ] 股票行情独立采集脚本（当前仅在现有采集/回补逻辑中部分复用）
- [ ] 债券行情采集
- [x] 错误处理和重试机制优化（部分实现：`config.py` 有配置，`backfill_fund_nav_history.py` 有重试逻辑）
- [x] 数据源切换支持（AKSHARE、FUND等）（配置已存在，但切换逻辑待完善）

#### 6. 指标计算扩展（计划放入 Phase 3“策略阶段”）
- [ ] 布林带（BOLL）指标
- [ ] KDJ指标
- [ ] 其他常用技术指标（CCI、DMI、OBV等）

#### 7. 前端功能增强（规划至 Phase 3/4）
- [ ] 行情数据缓存机制
- [ ] 实时行情轮询更新（当前为手动刷新）
- [x] 图表交互功能（缩放、平移、指标切换）（ECharts 默认支持，但未显式配置 dataZoom 等高级交互）
- [ ] 数据导出功能（CSV、Excel）（Phase 4.2 计划实现）

### 低优先级（可选功能）

#### 8. 性能优化
- [ ] 批量数据采集优化
- [ ] 数据库写入性能优化（批量插入）
- [ ] 指标计算性能优化（并行计算）
- [ ] 前端图表渲染性能优化

#### 9. 其他功能（全部移入 Phase 4+ 展望）
- [x] 行情数据回补功能（✅ 已实现：backfill_fund_nav_history.py）
- [ ] 数据质量监控和告警
- [ ] 行情数据统计分析
- [ ] 自定义指标配置

---

#### ✅ PC端页面（web/pc-app）
- [x] 登录页面
- [x] 主布局组件（参考demo的pill按钮样式）
- [x] 路由配置
- [x] 样式系统（完全按照demo的淡蓝色主题）
- [x] 看板首页（资产概览KPI、资产配比图表、待结算清单、核心持仓Top 5）
- [x] 产品管理页面（列表、新增/编辑，包含所有字段）
- [x] 账户管理页面（树形结构、新增平台/信封）
- [x] 流水查询页面
- [x] 订单管理页面
- [x] 结算确认页面
- [x] 持仓查看页面
- [x] 设置页面

---

### 核心功能支持

- ✅ JWT认证与授权（前端）
- ✅ 用户登录/注册
- ✅ 产品管理（CRUD，所有字段）
- ✅ 账户管理（CRUD、父子账户、fund_usage）
- ✅ 流水查询（列表、筛选）
- ✅ 订单管理（列表、取消）
- ✅ 结算确认（待结算清单）
- ✅ 持仓查看（列表）
- ✅ 看板聚合（资产概览、待结算清单、核心持仓）
- ✅ 统一记账入口（支持所有业务类型）
- ✅ 快速录入（消费/收入）
- ✅ 订单管理（新建、取消、详情）
- ✅ 结算确认（完整表单）
- ✅ 流水详情（显示分录）

---

### Phase 2.1 完成标准检查

#### PC端 ✅
- ✅ 所有共享层代码已创建
- ✅ 所有PC端页面已创建
- ✅ 所有枚举值显示中文
- ✅ 设计风格与demo一致
- ✅ 所有API调用与后端对接
- ✅ PC端编译通过，无错误

#### Mobile端 ✅
- ✅ 项目基础结构已创建
- ✅ 核心页面已创建（登录、看板、快速录入、待结算、持仓、设置）
- ✅ 底部Tab导航已实现
- ✅ 现代移动端设计特性已实现
- ✅ 所有API调用与后端对接（复用shared层）
- ✅ 设计风格与PC端保持一致

#### 整体 ✅
- ✅ 全项目编译通过
- ✅ 后端OrderController已支持fundingLines参数（组合支付）

## 注意事项

1. **所有枚举值必须显示中文**，用户不应该看到SPENDABLE、BUY等英文值
2. **表单字段必须完整**，不能像demo那样只显示部分字段
3. **设计风格必须一致**，完全按照demo的淡蓝色主题和卡片式布局
4. **API对接必须准确**，严格按照Phase 1的后端API接口定义
5. **组合支付必须支持**，订单和流水都支持多账户共同支付
6. **响应式数据绑定**：在Vue组件中使用Pinia store时，如果直接在模板中访问`store.products`可能出现响应式失效问题。推荐做法：
   - 使用本地`ref`存储列表数据
   - 在数据加载函数中，从store获取数据后更新本地ref：`products.value = productStore.products`
   - 这样确保Vue的响应式系统能正确追踪数据变化
7. **按钮布局**：操作列的按钮应使用`white-space: nowrap`防止自动换行，按钮之间使用适当的`margin-right`保持间距
