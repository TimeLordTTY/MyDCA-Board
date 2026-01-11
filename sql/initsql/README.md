# SQL初始化脚本说明

## 文件结构

```
sql/initsql/
├── DDL.sql                    # 完整数据库结构定义（主DDL文件）
├── DML.sql                    # 初始化数据（虚拟账户、管理员用户等）
├── README.md                  # 本文件
└── ARCHIVE_README.md          # 归档文件说明（如有）
```

## 执行顺序

### 新数据库初始化（推荐）

1. **执行 DDL.sql**
   - 创建所有表结构、索引、约束、视图
   - 适用于全新数据库

2. **执行 DML.sql**
   - 创建系统必需的虚拟账户
   - 创建默认管理员用户和家庭（可选）

### 现有数据库更新

请查看 `../updatesql/` 目录下的补丁脚本，按日期（YYYYMM）和编号顺序执行。

**执行原则**：
1. 按日期顺序：从最早的日期目录开始
2. 按编号顺序：在同一日期目录内，按文件编号（01、02、03...）顺序执行
3. 执行前检查：确认当前数据库版本，只执行未执行过的脚本

## 文件说明

### DDL.sql（主DDL文件）
- **用途**：完整的数据库结构定义
- **内容**：所有表的CREATE语句、索引、约束、视图
- **特点**：不包含任何INSERT语句，纯DDL
- **适用场景**：新数据库初始化
- **说明**：这是主DDL文件，包含所有最新的表结构定义

### DML.sql
- **用途**：初始化数据
- **内容**：
  1. 创建虚拟账户（系统必需）
  2. 创建管理员用户和默认家庭（可选）
- **特点**：使用ON DUPLICATE KEY UPDATE，可重复执行
- **适用场景**：新数据库初始化或补充缺失的系统数据

## 注意事项

1. **执行前准备**
   - 确保数据库已创建
   - 确保有足够的权限执行DDL和DML操作
   - 建议在空数据库中执行，避免表名冲突

2. **执行顺序**
   - 必须先执行DDL.sql，再执行DML.sql
   - DML.sql中的虚拟账户是系统必需的，建议必须执行

3. **管理员用户**
   - 默认用户名：`timelordtty`
   - 默认密码：`tty980626`
   - 如果用户已存在，DML.sql中的用户创建部分会失败，可手动删除或修改后再执行

## 验证查询

执行完DDL.sql和DML.sql后，可使用以下查询验证：

```sql
-- 验证表结构
SHOW TABLES;

-- 验证虚拟账户
SELECT account_code, account_name, account_kind, virtual_subtype 
FROM accounts 
WHERE account_kind = 'VIRTUAL' 
ORDER BY account_code;

-- 验证管理员用户
SELECT 
    u.id,
    u.username,
    u.nickname,
    u.family_id,
    f.family_name,
    ufr.role
FROM users u
LEFT JOIN families f ON u.family_id = f.id
LEFT JOIN user_family_roles ufr ON u.id = ufr.user_id AND f.id = ufr.family_id
WHERE u.username = 'timelordtty';
```
