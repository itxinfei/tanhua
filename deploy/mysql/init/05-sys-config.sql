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

-- ----------------------------
-- 种子数据：地图服务配置
-- 维护入口：后台「系统配置 → 地图服务」，改完 30 秒内全节点生效
-- 三类 Key 的分工（不可混用，高德按 Key 类型校验平台与接口）：
--   map.amap.js-key           前端 JSAPI Key（H5/小程序地图组件、地图选点），会下发到前端
--   map.amap.security-js-code JSAPI 2.0 安全密钥，与 js-key 配套，会下发到前端
--   map.amap.web-key          Web 服务 Key（逆地理编码/IP 定位），只在服务端使用，绝不下发
-- ----------------------------
INSERT INTO `tb_sys_config` (`config_key`, `config_value`, `description`)
SELECT 'map.enabled', '1', '地图能力总开关：1=启用，0=关闭（关闭后前端隐藏地图选点入口）'
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM `tb_sys_config` WHERE `config_key` = 'map.enabled'
);

INSERT INTO `tb_sys_config` (`config_key`, `config_value`, `description`)
SELECT 'map.amap.js-key', '', '高德前端 JS API Key（Web端(JS API)类型），用于地图展示与选点，会下发前端'
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM `tb_sys_config` WHERE `config_key` = 'map.amap.js-key'
);

INSERT INTO `tb_sys_config` (`config_key`, `config_value`, `description`)
SELECT 'map.amap.security-js-code', '', '高德 JS API 2.0 安全密钥，与 JS Key 配套，会下发前端'
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM `tb_sys_config` WHERE `config_key` = 'map.amap.security-js-code'
);

INSERT INTO `tb_sys_config` (`config_key`, `config_value`, `description`)
SELECT 'map.amap.web-key', '', '高德 Web 服务 Key（Web服务类型），用于逆地理编码与IP定位，仅服务端使用'
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM `tb_sys_config` WHERE `config_key` = 'map.amap.web-key'
);

SET FOREIGN_KEY_CHECKS=1;