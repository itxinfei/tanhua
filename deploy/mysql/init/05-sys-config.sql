-- ============================================================
-- 心飞交友 通用系统配置表（增量脚本，可重复执行）
-- 用途：运行时可调的系统级配置（IM 通道切换、推送开关等）
-- 管理入口：后台管理系统「系统配置」页（xfjy-admin im-config.vue）
-- 缓存策略：Redis 30s TTL，改配置约 30 秒内全节点生效，无需重启
-- ============================================================
USE xfjy;
SET NAMES utf8mb4;

SET FOREIGN_KEY_CHECKS=0;

-- ----------------------------
-- Table structure for tb_sys_config
-- ----------------------------
CREATE TABLE IF NOT EXISTS `tb_sys_config` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `config_key` varchar(100) NOT NULL COMMENT '配置键，如 im.provider',
  `config_value` varchar(500) DEFAULT NULL COMMENT '配置值',
  `description` varchar(200) DEFAULT NULL COMMENT '配置说明',
  `created` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_config_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通用系统配置表';

-- ----------------------------
-- 种子数据：IM 通道配置（默认开源 WuKongIM，可后台切换回环信）
-- 可选值：wukongim（默认，开源） / huanxin（环信，第三方）
-- ----------------------------
INSERT INTO `tb_sys_config` (`config_key`, `config_value`, `description`)
SELECT 'im.provider', 'wukongim', 'IM 服务通道：wukongim=开源WuKongIM（默认），huanxin=环信第三方'
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM `tb_sys_config` WHERE `config_key` = 'im.provider'
);

SET FOREIGN_KEY_CHECKS=1;