# MySQL 字符集乱码诊断与修复指南

> 事件：2026-11-03 发现 192.168.20.133 Docker MySQL `xfjy` 库中文乱码，涉及数据行和表/列 COMMENT 元数据。
> 本文沉淀完整诊断方法论与修复方案，避免同类问题再次发生或发生时能快速定位。

---

## 一、根因：Docker MySQL 初始化字符集陷阱

### 1.1 MySQL 字符集三层模型

MySQL 连接涉及三个字符集变量，必须一致才不会乱码：

| 变量 | 作用 |
|------|------|
| `character_set_client` | 客户端**发送**的 SQL 语句按什么编码解析 |
| `character_set_connection` | 连接层将 SQL 转换到此编码再执行 |
| `character_set_results` | 服务器**返回**结果按什么编码编码 |

`SET NAMES utf8mb4` 一次性设置这三个变量为 utf8mb4。

### 1.2 乱码怎么产生的

```
正确UTF-8 SQL文件 ──→ mysql客户端(默认latin1读取) ──→ 服务器(按latin1解码→utf8mb4存储)
   "探" = E6 8E A2        被当作3个latin1字符          每个字节再编码为2字节UTF-8
                          E6→æ, 8E→Ž, A2→¢            æ→C3A6, Ž→C5BD, ¢→C2A2
```

**关键**：MySQL 官方 Docker 镜像执行 `/docker-entrypoint-initdb.d/*.sql` 时，客户端默认字符集是 `latin1`（而非 utf8mb4），即使库表声明了 `CHARSET=utf8mb4`。UTF-8 中文被按 latin1 逐字节误读，再转 utf8mb4 存储——这就是**双重编码（double-encoding）**。

### 1.3 确认根因的命令

```bash
# 进容器查看字符集变量
docker exec xfjy-mysql mysql -uroot -p123456 -e "SHOW VARIABLES LIKE 'character_set%'"
# 关键输出：character_set_client = latin1 ← 根因
```

---

## 二、乱码形态诊断：三种形态及 HEX() 确诊法

### 2.1 用 HEX() 看字节真相

```sql
-- 查看某列原始字节（不要相信终端显示，只信 HEX）
SELECT id, title, HEX(title) FROM tb_announcement LIMIT 3;
```

### 2.2 三种形态对照表

| 形态 | HEX 特征 | 产生原因 | 可逆性 |
|------|----------|----------|--------|
| **双重编码** | `C3A6 C5BD C2A2`（正常是 `E6 8E A2`） | UTF-8 被按 latin1 误读再转 utf8mb4 | ✅ 可逆 |
| **U+FFFD 替换** | 含 `EFBFBD` | GBK 编码写入被替换为 Unicode 替换符 | ❌ 不可逆 |
| **GBK 形态** | 终端显示 `閫氱敤绯荤粺閰嶇疆琛` | UTF-8 被按 GBK 误读 | 视情况可逆 |

**判断口诀**：
- 中文每字正常 UTF-8 是 3 字节；双重编码后变 6 字节（每字节膨胀为 `C3xx`/`C2xx`/`C5xx`）
- `EFBFBD` 出现 = 原始字节已丢失，无法还原
- 显示为生僻汉字组合（如"閰嶇疆閿"）= UTF-8 字节被按 GBK 解读

### 2.3 全量扫描定位乱码行

```sql
-- 双重编码特征匹配（Ã/æ/ç 等拉丁扩展字符）
SELECT COUNT(*) FROM tb_xxx WHERE col LIKE '%Ã%' OR col LIKE '%æ%' OR col LIKE '%ç%';

-- U+FFFD 匹配（用 CHAR(0xEFBFBD) 拼接，避免特殊字符丢失）
SELECT COUNT(*) FROM tb_xxx WHERE col LIKE CONCAT('%', CHAR(0xEFBFBD USING utf8mb4), '%');

-- COMMENT 元数据扫描
SELECT TABLE_NAME, TABLE_COMMENT FROM information_schema.TABLES
  WHERE TABLE_SCHEMA='xfjy' AND TABLE_COMMENT REGEXP '[À-ÿ]';
SELECT TABLE_NAME, COLUMN_NAME, COLUMN_COMMENT FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA='xfjy' AND COLUMN_COMMENT REGEXP '[À-ÿ]';
```

---

## 三、修复方法

### 3.1 双重编码：CONVERT 回转（可逆）

**原理**：逆向操作——先按 latin1 取回原始字节（CAST AS BINARY），再按 utf8mb4 正确解码。

```sql
-- 修复前必须备份！
-- mysqldump -uroot -p xfjy tb_announcement tb_question tb_agreement > backup.sql

-- 单列修复模板
UPDATE tb_xxx SET col = CONVERT(CAST(CONVERT(col USING latin1) AS BINARY) USING utf8mb4);

-- 多列同时修复
UPDATE tb_announcement SET
  title       = CONVERT(CAST(CONVERT(title       USING latin1) AS BINARY) USING utf8mb4),
  description = CONVERT(CAST(CONVERT(description USING latin1) AS BINARY) USING utf8mb4);

-- 修复后验证
SELECT title, HEX(title) FROM tb_announcement LIMIT 3;
-- 预期：HEX 中不再有 C3A6/C5BD/C2A2 双重编码模式
```

**注意**：回转前执行的那条会话必须 `SET NAMES utf8mb4`，否则修复本身又会产生新的双重编码。

### 3.2 U+FFFD 不可逆损坏：删除重灌

`EFBFBD` 意味着原始字节已被 MySQL 用替换符覆盖，任何 CONVERT 都无法还原。

```sql
-- 确认损坏范围
SELECT id, HEX(col) FROM tb_xxx WHERE col LIKE CONCAT('%', CHAR(0xEFBFBD USING utf8mb4), '%');
-- 只能删除脏数据（测试数据）或从备份/源头重新导入
DELETE FROM tb_review WHERE id IN (1, 2);
```

### 3.3 COMMENT 元数据乱码：ALTER 重写

**关键区别**：COMMENT 是表/列定义的元数据，不是行数据，CONVERT 回转对它无效。必须用 `ALTER` 重新声明正确的注释。

```sql
-- 修复表注释
ALTER TABLE `tb_sys_config` COMMENT '通用系统配置表';

-- 修复列注释（⚠️ MODIFY COLUMN 必须完整重声明列类型和约束，否则会丢失默认值/NOT NULL 等）
ALTER TABLE `tb_agreement` MODIFY COLUMN `type` varchar(20) NOT NULL COMMENT 'USER-用户协议 PRIVACY-隐私政策';
```

**安全做法**：从 `information_schema.COLUMNS` 导出当前列定义（类型/NULL/DEFAULT/EXTRA），只替换 COMMENT 部分，确保不改类型。本项目用 [`scripts/gen-fix-comments.cjs`](../scripts/gen-fix-comments.cjs) 自动完成此对比生成。

### 3.4 GBK 形态乱码：cp1252 反向映射解码

当乱码 COMMENT 在 init SQL 中找不到正确源时，可用脚本自动回转：

```
原理：UTF-8 字节被按 latin1/cp1252 误读 → 每个字符的码点就是原始字节值
     （0x80-0x9F 区是 cp1252 特殊映射，如 € → 0x80）
     → 把字符码点还原为字节 → 按 UTF-8 解码即得原文
```

见 [`scripts/gen-fix-comments.cjs`](../scripts/gen-fix-comments.cjs) 中的 `decodeMojibake()` 函数。

---

## 四、防复发措施（已实施）

### 4.1 init SQL 强制 SET NAMES

所有 `/docker-entrypoint-initdb.d/` 下的 SQL 文件，在 `USE xfjy;` 后立即声明：

```sql
USE xfjy;
SET NAMES utf8mb4;   -- ← 防止 entrypoint 用 latin1 读取 UTF-8 文件
```

已覆盖的文件（[`deploy/mysql/init/`](../deploy/mysql/init/)）：
- `01-init.sql` / `02-schema.sql` / `03-agreement.sql` / `04-user-device.sql` / `05-sys-config.sql`

### 4.2 同步脚本模板

[`scripts/sync-mysql-init.ps1`](../scripts/sync-mysql-init.ps1) 的 `Write-SqlFile` 模板已内置 `SET NAMES utf8mb4;`，重新生成的 init SQL 自动携带。

### 4.3 手动导入也要加

任何手动 `mysql < xxx.sql` 导入，必须带字符集参数：

```bash
mysql -uroot -p --default-character-set=utf8mb4 xfjy < scripts/xfjy.sql
```

### 4.4 连接串检查

应用层 JDBC URL 必须声明字符集（本项目 Spring Boot 配置已含）：

```
jdbc:mysql://host:3306/xfjy?characterEncoding=utf8mb4&useSSL=false
```

---

## 五、排查命令速查

```bash
# ===== 诊断 =====
# 1. 查看容器字符集变量
docker exec xfjy-mysql mysql -uroot -p123456 -e "SHOW VARIABLES LIKE 'character_set%'"

# 2. 某表数据乱码扫描
docker exec xfjy-mysql mysql -uroot -p123456 --default-character-set=utf8mb4 xfjy \
  -e "SELECT id, title, HEX(title) FROM tb_announcement LIMIT 5"

# 3. 全表 COMMENT 乱码扫描
docker exec xfjy-mysql mysql -uroot -p123456 --default-character-set=utf8mb4 \
  -e "SELECT TABLE_NAME, TABLE_COMMENT FROM information_schema.TABLES WHERE TABLE_SCHEMA='xfjy' AND TABLE_COMMENT REGEXP '[À-ÿ]'"

# ===== 修复 =====
# 4. 备份受影响表
docker exec xfjy-mysql mysqldump -uroot -p123456 xfjy tb_announcement > backup.sql

# 5. 执行修复 SQL（先 SET NAMES！）
docker exec xfjy-mysql mysql -uroot -p123456 --default-character-set=utf8mb4 xfjy < fix.sql

# ===== 验证 =====
# 6. 验证修复结果
docker exec xfjy-mysql mysql -uroot -p123456 --default-character-set=utf8mb4 \
  -e "SELECT TABLE_NAME, TABLE_COMMENT FROM information_schema.TABLES WHERE TABLE_SCHEMA='xfjy'"
```

---

## 六、工具脚本索引

| 脚本 | 用途 |
|------|------|
| [`scripts/fix-133-charset.sql`](../scripts/fix-133-charset.sql) | 数据层乱码修复（CONVERT 回转 3 表 + DELETE 脏数据 2 行） |
| [`scripts/fix-133-comments.sql`](../scripts/fix-133-comments.sql) | COMMENT 元数据修复（8 表注释 + 43 列注释 ALTER 重写） |
| [`scripts/gen-fix-comments.cjs`](../scripts/gen-fix-comments.cjs) | COMMENT 修复 SQL 生成器：解析 init SQL 提取正确注释 → 对比 DB 元数据 → 生成 ALTER；含 cp1252 反向映射自动解码无源乱码 |

---

## 七、经验总结

1. **不要相信终端显示**——乱码诊断只认 `HEX()`，显示层可能二次误导
2. **CONVERT 回转只对行数据有效**——COMMENT 元数据必须 ALTER 重写
3. **MODIFY COLUMN 是全量替换**——只改注释时必须带上完整列定义，否则丢默认值/约束
4. **修复前必须备份**——`mysqldump` 受影响表，或 `--no-data` 备份 schema
5. **修复会话必须 SET NAMES utf8mb4**——否则修复操作本身产生新的双重编码
6. **U+FFFD = 不可逆**——遇到 `EFBFBD` 直接放弃回转，从源头重新导入
7. **防复发的本质**：让 MySQL 客户端连接字符集与 SQL 文件编码一致（utf8mb4），`SET NAMES` 是最可靠的手段