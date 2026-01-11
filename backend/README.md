# 财富中枢系统 - 后端服务

## 项目信息

- **包名**: `com.timelordtty.dca`
- **服务端口**: 8765
- **接口版本**: `/api/v2/`
- **Java版本**: 17
- **Spring Boot版本**: 3.2.0

## 技术栈

- Spring Boot 3.x
- MyBatis 3.0.3+
- MySQL 8.0
- JWT (jjwt 0.12.3)
- Spring Security Crypto (BCrypt)
- Lombok
- Spring Boot Actuator

## 启动方式

### 开发环境

```bash
mvn spring-boot:run
```

或使用IDE直接运行 `WealthHubApplication.java`

### 生产环境

```bash
mvn clean package
java -jar target/wealth-hub-1.0.0.jar --spring.profiles.active=prod
```

## 配置文件

- `application.yml` - 主配置文件
- `application-dev.yml` - 开发环境配置
- `application-prod.yml` - 生产环境配置（需创建）

## 数据库配置

数据库配置从 `config/db_config.json` 读取，当前配置：
- Host: 127.0.0.1
- Port: 9009
- Database: dca_v2
- User: dca

## MyBatis配置

- Mapper XML位置: `classpath:mapper/*.xml`
- 实体类包: `com.timelordtty.dca.model`
- 下划线转驼峰: 已开启

## 健康检查

启动后访问：`http://localhost:8765/actuator/health`

## 项目结构

```
src/main/java/com/timelordtty/dca/
├── WealthHubApplication.java    # 主启动类
├── config/                      # 配置类
├── controller/                  # REST控制器
├── service/                     # 业务逻辑层
├── mapper/                      # MyBatis Mapper接口
├── model/                       # 实体类
├── dto/                         # 数据传输对象
├── exception/                   # 异常处理
├── security/                    # 安全配置
└── scheduler/                   # 定时任务

src/main/resources/
├── mapper/                      # MyBatis XML映射文件
├── application.yml
└── application-dev.yml
```

## 开发规范

1. 所有Controller使用 `@RequestMapping("/api/v2")` 作为基础路径
2. 使用MyBatis而非JPA
3. 实体类使用Lombok简化代码
4. 密码使用BCrypt加密存储
5. JWT Token在请求头中传递：`Authorization: Bearer <token>`

## 下一步开发

参考 [../docs/开发实施指南.md](../docs/开发实施指南.md) 进行Phase 1开发。

