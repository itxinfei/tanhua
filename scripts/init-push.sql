-- ============================================================
-- 极光推送：用户设备绑定表（registrationId 绑定）
-- 幂等导入（utf8mb4 避免中文乱码）：
--   mysql -u root -p --default-character-set=utf8mb4 xfjy < scripts/init-push.sql
-- ============================================================

-- ----------------------------
-- 用户设备绑定表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `tb_user_device` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT,
  `user_id`         BIGINT       NOT NULL COMMENT '用户ID',
  `registration_id` VARCHAR(128) NOT NULL COMMENT '极光推送标识',
  `platform`        VARCHAR(16)  DEFAULT NULL COMMENT '平台 ANDROID/IOS',
  `created`         DATETIME     DEFAULT CURRENT_TIMESTAMP,
  `updated`         DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_rid` (`user_id`, `registration_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户设备绑定(极光推送)';