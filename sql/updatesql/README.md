# SQL更新脚本目录

## 目录结构

```
sql/updatesql/
├── YYYYMM/              # 按年月组织的目录（如：202401、202402）
│   ├── 01_*.sql        # 编号从01开始，按执行顺序递增
│   ├── 02_*.sql
│   └── ...
└── README.md           # 本文件
```

## 使用说明

### 执行原则

1. **按日期顺序执行**：从最早的日期目录开始，依次执行
2. **按编号顺序执行**：在同一日期目录内，按文件编号顺序执行（01、02、03...）
3. **执行前检查**：确认当前数据库版本，只执行未执行过的脚本

### 示例

假设当前数据库是2024年1月创建的，需要更新到最新：

```bash
# 执行2024年1月的更新
mysql -u user -p database < updatesql/202401/01_init_admin_user.sql

# 执行2024年2月的更新（如果有）
mysql -u user -p database < updatesql/202402/01_*.sql
mysql -u user -p database < updatesql/202402/02_*.sql
```

### 文件命名规范

- **目录名**：`YYYYMM` 格式（如：`202401` 表示2024年1月）
- **文件名**：`NN_description.sql` 格式
  - `NN`：两位数字编号（01、02、03...），表示执行顺序
  - `description`：简短描述，使用下划线分隔

### 当前更新脚本

#### 2024年1月（202401）

- `01_init_admin_user.sql` - 初始化系统管理员用户
  - 说明：此脚本内容已合并到 `initsql/DML.sql` 中
  - 如果是从全新数据库初始化，请使用 `initsql/DML.sql`
  - 如果是在现有数据库中补充管理员用户，可使用此脚本

## 注意事项

1. **备份数据库**：执行更新脚本前，建议先备份数据库
2. **测试环境验证**：先在测试环境执行，确认无误后再在生产环境执行
3. **版本记录**：建议在数据库中记录已执行的脚本版本，避免重复执行
4. **回滚方案**：重要更新应准备回滚脚本

## 版本记录表（建议）

可以在数据库中创建以下表来记录已执行的更新脚本：

```sql
CREATE TABLE IF NOT EXISTS `schema_migrations` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `script_path` VARCHAR(255) NOT NULL COMMENT '脚本路径',
  `executed_at` DATETIME NOT NULL COMMENT '执行时间',
  `executed_by` VARCHAR(64) NULL COMMENT '执行人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_script_path` (`script_path`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据库更新脚本执行记录';
```
