-- tb_user.mobile 唯一索引（P0：并发首次登录会插出两个同手机号账号）
-- 幂等：可重复执行；已存在同名索引时跳过
-- 前置：执行前确认没有重复手机号，否则 ALTER 会失败并提示 Duplicate entry
--   SELECT mobile, COUNT(*) c FROM tb_user GROUP BY mobile HAVING c > 1;
USE xfjy;
SET NAMES utf8mb4;

-- ----------------------------
-- 1) 新增唯一索引 uk_user_mobile(mobile)
--    mobile 允许为 NULL：MySQL 唯一索引允许多个 NULL，不影响未绑定手机号的存量行
-- ----------------------------
SET @has_uk := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tb_user' AND INDEX_NAME = 'uk_user_mobile'
);
SET @ddl_add_uk := IF(@has_uk = 0,
  'ALTER TABLE `tb_user` ADD UNIQUE KEY `uk_user_mobile` (`mobile`)',
  'SELECT ''uk_user_mobile already exists'' AS msg');
PREPARE stmt_add_uk FROM @ddl_add_uk;
EXECUTE stmt_add_uk;
DEALLOCATE PREPARE stmt_add_uk;

-- ----------------------------
-- 2) 删除被唯一索引完全覆盖的旧普通索引 mobile（减少一次索引维护开销）
-- ----------------------------
SET @has_old := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tb_user' AND INDEX_NAME = 'mobile'
);
SET @ddl_drop_old := IF(@has_old > 0,
  'ALTER TABLE `tb_user` DROP INDEX `mobile`',
  'SELECT ''index mobile already dropped'' AS msg');
PREPARE stmt_drop_old FROM @ddl_drop_old;
EXECUTE stmt_drop_old;
DEALLOCATE PREPARE stmt_drop_old;

-- ----------------------------
-- 3) 结果核对
-- ----------------------------
SHOW INDEX FROM `tb_user` WHERE Key_name = 'uk_user_mobile';
