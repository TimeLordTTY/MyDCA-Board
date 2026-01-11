# 财富中枢系统 (Wealth Hub)

**作者**：timelordtty

## 项目简介

财富中枢系统是一个完整的个人/家庭财富管理平台，支持资产盘点、统一记账、投资建议、策略回测等功能。

## 技术栈

### 后端
- **Java 17+** (Spring Boot 3.x)
- **MyBatis** (不使用JPA)
- **MySQL 8.0**
- **JWT** 认证
- **Spring Security Crypto** (BCrypt密码加密)

### 前端
- **Vue 3 + TypeScript**
- **PC端**: Element Plus
- **Mobile端**: Vant 4
- **状态管理**: Pinia
- **构建工具**: Vite

### Python脚本
- **Python 3.9+**
- 用于行情同步、指标计算、策略执行、回测等

## 项目结构

```
MyDCA-Board/
├── backend/                    # Java后端
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/timelordtty/dca/
│   │   │   └── resources/
│   │   └── test/
│   └── pom.xml
├── web/                        # 前端
│   ├── shared/                 # 共享核心逻辑
│   ├── pc-app/                 # PC端
│   └── mobile-app/             # Mobile端
├── scripts/                    # Python脚本
│   ├── market/                 # 行情相关
│   ├── indicator/              # 指标计算
│   ├── strategy/               # 策略执行
│   └── backtest/               # 回测
├── scripts/sql/                # SQL脚本
│   └── initsql/
│       └── DDL_v2_revised.sql
├── config/                     # 配置文件
│   └── db_config.json
└── docs/                       # 文档
    ├── 财富中枢系统完整设计方案.md
    └── 开发实施指南.md
```

## 快速开始

### 环境要求

- JDK 17+
- Maven 3.8+
- Node.js 18+
- MySQL 8.0+
- Python 3.9+

### 数据库初始化

1. 创建数据库：
```sql
CREATE DATABASE dca_v2 CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
```

2. 执行DDL脚本：
```bash
mysql -u dca -p dca_v2 < scripts/sql/initsql/DDL_v2_revised.sql
```

### 后端启动

```bash
cd backend
mvn clean install
mvn spring-boot:run
```

后端服务将在 `http://localhost:8765` 启动。

### 前端启动

#### PC端
```bash
cd web/pc-app
npm install
npm run dev
```

PC端将在 `http://localhost:3000` 启动。

#### Mobile端
```bash
cd web/mobile-app
npm install
npm run dev
```

Mobile端将在 `http://localhost:3001` 启动。

#### 共享层
```bash
cd web/shared
npm install
npm run build
```

## 开发指南

详细的开发指南请参考 [docs/开发实施指南.md](docs/开发实施指南.md)

## 接口规范

- 基础路径：`/api/v2/`
- 认证方式：JWT Bearer Token
- 请求头：`Authorization: Bearer <token>`

## 开发阶段

当前处于 **项目初始化阶段**，已完成：
- ✅ 项目目录结构
- ✅ Java后端基础配置
- ✅ 前端项目初始化
- ✅ 数据库DDL脚本

下一步：Phase 1 - MVP阶段（用户与权限模块）

## 许可证

MIT

