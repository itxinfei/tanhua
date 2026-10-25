/*
Navicat MySQL Data Transfer

Source Server         : 127.0.0.1-3306
Source Server Version : 50622
Source Host           : 127.0.0.1:3306
Source Database       : mytanhua

Target Server Type    : MYSQL
Target Server Version : 50622
File Encoding         : 65001

Date: 2019-11-04 21:05:14
*/

SET FOREIGN_KEY_CHECKS=0;

-- ----------------------------
-- Table structure for tb_announcement
-- ----------------------------
DROP TABLE IF EXISTS `tb_announcement`;
CREATE TABLE `tb_announcement` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `title` varchar(200) DEFAULT NULL COMMENT '标题',
  `description` text COMMENT '描述',
  `created` datetime DEFAULT NULL,
  `updated` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `created` (`created`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8 COMMENT='公告表';

-- ----------------------------
-- Records of tb_announcement
-- ----------------------------
INSERT INTO `tb_announcement` VALUES ('1', '探花新版本上线发布啦～,盛夏high趴开始了，赶紧来报名吧！', '探花App2019年7月23日起在苹果商店…,浓情夏日，清爽一聚，探花将吧大家聚…', '2019-10-14 11:06:34', '2019-10-14 11:06:37');
INSERT INTO `tb_announcement` VALUES ('2', '探花交友的圈子功能正式上线啦~~', '探花交友的圈子功能正式上线啦，欢迎使用~', '2019-10-14 11:09:31', '2019-10-14 11:09:33');
INSERT INTO `tb_announcement` VALUES ('3', '国庆放假期间，探花交友正常使用~', '国庆放假期间，探花交友正常使用~', '2019-10-14 11:10:01', '2019-10-14 11:10:04');

-- ----------------------------
-- Table structure for tb_black_list
-- ----------------------------
DROP TABLE IF EXISTS `tb_black_list`;
CREATE TABLE `tb_black_list` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `user_id` bigint(20) DEFAULT NULL,
  `black_user_id` bigint(20) DEFAULT NULL,
  `created` datetime DEFAULT NULL,
  `updated` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `user_id` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8 COMMENT='黑名单';

-- ----------------------------
-- Records of tb_black_list
-- ----------------------------
INSERT INTO `tb_black_list` VALUES ('1', '1', '22', '2019-11-01 15:47:22', '2019-11-01 15:47:24');
INSERT INTO `tb_black_list` VALUES ('2', '1', '23', '2019-11-01 15:47:39', '2019-11-01 15:47:42');

-- ----------------------------
-- Table structure for tb_question
-- ----------------------------
DROP TABLE IF EXISTS `tb_question`;
CREATE TABLE `tb_question` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `user_id` bigint(20) DEFAULT NULL COMMENT '用户id',
  `txt` varchar(200) DEFAULT NULL COMMENT '问题内容',
  `created` datetime DEFAULT NULL,
  `updated` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `user_id` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8;

-- ----------------------------
-- Records of tb_question
-- ----------------------------
INSERT INTO `tb_question` VALUES ('1', '1', '你确定真的喜欢我吗？', '2019-10-20 17:25:58', '2019-11-04 14:48:35');
INSERT INTO `tb_question` VALUES ('2', '57', '你喜欢什么颜色？', '2019-10-20 23:17:39', '2019-10-20 23:17:41');

-- ----------------------------
-- Table structure for tb_settings
-- ----------------------------
DROP TABLE IF EXISTS `tb_settings`;
CREATE TABLE `tb_settings` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `user_id` bigint(20) DEFAULT NULL,
  `like_notification` tinyint(4) DEFAULT '1' COMMENT '推送喜欢通知',
  `pinglun_notification` tinyint(4) DEFAULT '1' COMMENT '推送评论通知',
  `gonggao_notification` tinyint(4) DEFAULT '1' COMMENT '推送公告通知',
  `created` datetime DEFAULT NULL,
  `updated` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8 COMMENT='设置表';

-- ----------------------------
-- Records of tb_settings
-- ----------------------------
INSERT INTO `tb_settings` VALUES ('1', '1', '1', '1', '1', '2019-11-03 11:58:36', '2019-11-04 13:36:40');

-- ----------------------------
-- Table structure for tb_user
-- ----------------------------
DROP TABLE IF EXISTS `tb_user`;
CREATE TABLE `tb_user` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `mobile` varchar(11) DEFAULT NULL COMMENT '手机号',
  `password` varchar(32) DEFAULT NULL COMMENT '密码，需要加密',
  `created` datetime DEFAULT NULL,
  `updated` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `mobile` (`mobile`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=104 DEFAULT CHARSET=utf8 COMMENT='用户表';

-- ----------------------------
-- Records of tb_user
-- ----------------------------
INSERT INTO `tb_user` VALUES ('1', '17602026868', 'e10adc3949ba59abbe56e057f20f883e', '2019-08-02 16:43:46', '2019-11-04 15:48:06');
INSERT INTO `tb_user` VALUES ('2', '15800807988', 'e10adc3949ba59abbe56e057f20f883e', '2019-08-02 16:50:32', '2019-08-02 16:50:32');
INSERT INTO `tb_user` VALUES ('3', '13172725874', 'e10adc3949ba59abbe56e057f20f883e', '2019-08-02 16:43:46', '2019-08-02 16:43:46');
INSERT INTO `tb_user` VALUES ('4', '13877469758', 'e10adc3949ba59abbe56e057f20f883e', '2019-08-02 16:43:46', '2019-08-02 16:43:46');
INSERT INTO `tb_user` VALUES ('5', '13037577648', 'e10adc3949ba59abbe56e057f20f883e', '2019-08-02 16:43:46', '2019-08-02 16:43:46');
INSERT INTO `tb_user` VALUES ('6', '13079029863', 'e10adc3949ba59abbe56e057f20f883e', '2019-08-02 16:43:46', '2019-08-02 16:43:46');
INSERT INTO `tb_user` VALUES ('7', '13024673388', 'e10adc3949ba59abbe56e057f20f883e', '2019-08-02 16:43:46', '2019-08-02 16:43:46');
INSERT INTO `tb_user` VALUES ('8', '13235171211', 'e10adc3949ba59abbe56e057f20f883e', '2019-08-02 16:43:46', '2019-08-02 16:43:46');
INSERT INTO `tb_user` VALUES ('9', '13554575271', 'e10adc3949ba59abbe56e057f20f883e', '2019-08-02 16:43:46', '2019-08-02 16:43:46');
INSERT INTO `tb_user` VALUES ('10', '13006615355', 'e10adc3949ba59abbe56e057f20f883e', '2019-08-02 16:43:46', '2019-08-02 16:43:46');
INSERT INTO `tb_user` VALUES ('11', '13496418811', 'e10adc3949ba59abbe56e057f20f883e', '2019-08-02 16:43:46', '2019-08-02 16:43:46');
INSERT INTO `tb_user` VALUES ('12', '13501605527', 'e10adc3949ba59abbe56e057f20f883e', '2019-08-02 16:43:46', '2019-08-02 16:43:46');
INSERT INTO `tb_user` VALUES ('13', '13814273863', 'e10adc3949ba59abbe56e057f20f883e', '2019-08-02 16:43:46', '2019-08-02 16:43:46');
INSERT INTO `tb_user` VALUES ('14', '13980560088', 'e10adc3949ba59abbe56e057f20f883e', '2019-08-02 16:43:46', '2019-08-02 16:43:46');
INSERT INTO `tb_user` VALUES ('15', '13287655532', 'e10adc3949ba59abbe56e057f20f883e', '2019-08-02 16:43:46', '2019-08-02 16:43:46');
INSERT INTO `tb_user` VALUES ('16', '13392532132', 'e10adc3949ba59abbe56e057f20f883e', '2019-08-02 16:43:46', '2019-08-02 16:43:46');
INSERT INTO `tb_user` VALUES ('17', '13305577548', 'e10adc3949ba59abbe56e057f20f883e', '2019-08-02 16:43:46', '2019-08-02 16:43:46');
INSERT INTO `tb_user` VALUES ('18', '13056740178', 'e10adc3949ba59abbe56e057f20f883e', '2019-08-02 16:43:46', '2019-08-02 16:43:46');
INSERT INTO `tb_user` VALUES ('19', '13968734556', 'e10adc3949ba59abbe56e057f20f883e', '2019-08-02 16:43:46', '2019-08-02 16:43:46');
INSERT INTO `tb_user` VALUES ('20', '13790585392', 'e10adc3949ba59abbe56e057f20f883e', '2019-08-02 16:43:46', '2019-08-02 16:43:46');
INSERT INTO `tb_user` VALUES ('21', '13783990035', 'e10adc3949ba59abbe56e057f20f883e', '2019-08-02 16:43:46', '2019-08-02 16:43:46');
INSERT INTO `tb_user` VALUES ('22', '13955001055', 'e10adc3949ba59abbe56e057f20f883e', '2019-08-02 16:43:46', '2019-08-02 16:43:46');
INSERT INTO `tb_user` VALUES ('23', '13503888939', 'e10adc3949ba59abbe56e057f20f883e', '2019-08-02 16:43:46', '2019-08-02 16:43:46');
INSERT INTO `tb_user` VALUES ('24', '13584175268', 'e10adc3949ba59abbe56e057f20f883e', '2019-08-02 16:43:46', '2019-08-02 16:43:46');
INSERT INTO `tb_user` VALUES ('25', '13381313372', 'e10adc3949ba59abbe56e057f20f883e', '2019-08-02 16:43:46', '2019-08-02 16:43:46');
INSERT INTO `tb_user` VALUES ('26', '13255389620', 'e10adc3949ba59abbe56e057f20f883e', '2019-08-02 16:43:46', '2019-08-02 16:43:46');
INSERT INTO `tb_user` VALUES ('27', '13690910957', 'e10adc3949ba59abbe56e057f20f883e', '2019-08-02 16:43:46', '2019-08-02 16:43:46');
INSERT INTO `tb_user` VALUES ('28', '13376147486', 'e10adc3949ba59abbe56e057f20f883e', '2019-08-02 16:43:46', '2019-08-02 16:43:46');
INSERT INTO `tb_user` VALUES ('29', '13467015997', 'e10adc3949ba59abbe56e057f20f883e', '2019-08-02 16:43:46', '2019-08-02 16:43:46');
INSERT INTO `tb_user` VALUES ('30', '13248053315', 'e10adc3949ba59abbe56e057f20f883e', '2019-08-02 16:43:46', '2019-08-02 16:43:46');
INSERT INTO `tb_user` VALUES ('31', '13618464100', 'e10adc3949ba59abbe56e057f20f883e', '2019-08-02 16:43:46', '2019-08-02 16:43:46');
INSERT INTO `tb_user` VALUES ('32', '13443447446', 'e10adc3949ba59abbe56e057f20f883e', '2019-08-02 16:43:46', '2019-08-02 16:43:46');
INSERT INTO `tb_user` VALUES ('33', '13125719539', 'e10adc3949ba59abbe56e057f20f883e', '2019-08-02 16:43:46', '2019-08-02 16:43:46');
INSERT INTO `tb_user` VALUES ('34', '13072800811', 'e10adc3949ba59abbe56e057f20f883e', '2019-08-02 16:43:46', '2019-08-02 16:43:46');
INSERT INTO `tb_user` VALUES ('35', '13145571979', 'e10adc3949ba59abbe56e057f20f883e', '2019-08-02 16:43:46', '2019-08-02 16:43:46');
INSERT INTO `tb_user` VALUES ('36', '13720672325', 'e10adc3949ba59abbe56e057f20f883e', '2019-08-02 16:43:46', '2019-08-02 16:43:46');
INSERT INTO `tb_user` VALUES ('37', '13055381341', 'e10adc3949ba59abbe56e057f20f883e', '2019-08-02 16:43:46', '2019-08-02 16:43:46');
INSERT INTO `tb_user` VALUES ('38', '13349885884', 'e10adc3949ba59abbe56e057f20f883e', '2019-08-02 16:43:46', '2019-08-02 16:43:46');
INSERT INTO `tb_user` VALUES ('39', '13347146816', 'e10adc3949ba59abbe56e057f20f883e', '2019-08-02 16:43:46', '2019-08-02 16:43:46');
INSERT INTO `tb_user` VALUES ('40', '13179664691', 'e10adc3949ba59abbe56e057f20f883e', '2019-08-02 16:43:46', '2019-08-02 16:43:46');
INSERT INTO `tb_user` VALUES ('41', '13766900927', 'e10adc3949ba59abbe56e057f20f883e', '2019-08-02 16:43:46', '2019-08-02 16:43:46');
INSERT INTO `tb_user` VALUES ('42', '13151553585', 'e10adc3949ba59abbe56e057f20f883e', '2019-08-02 16:43:46', '2019-08-02 16:43:46');
INSERT INTO `tb_user` VALUES ('43', '13391205369', 'e10adc3949ba59abbe56e057f20f883e', '2019-08-02 16:43:46', '2019-08-02 16:43:46');
INSERT INTO `tb_user` VALUES ('44', '13133094367', 'e10adc3949ba59abbe56e057f20f883e', '2019-08-02 16:43:46', '2019-08-02 16:43:46');
INSERT INTO `tb_user` VALUES ('45', '13281763998', 'e10adc3949ba59abbe56e057f20f883e', '2019-08-02 16:43:46', '2019-08-02 16:43:46');
INSERT INTO `tb_user` VALUES ('46', '13936660584', 'e10adc3949ba59abbe56e057f20f883e', '2019-08-02 16:43:46', '2019-08-02 16:43:46');
INSERT INTO `tb_user` VALUES ('47', '13052316704', 'e10adc3949ba59abbe56e057f20f883e', '2019-08-02 16:43:46', '2019-08-02 16:43:46');
INSERT INTO `tb_user` VALUES ('48', '13324491235', 'e10adc3949ba59abbe56e057f20f883e', '2019-08-02 16:43:46', '2019-08-02 16:43:46');
INSERT INTO `tb_user` VALUES ('49', '13314858500', 'e10adc3949ba59abbe56e057f20f883e', '2019-08-02 16:43:46', '2019-08-02 16:43:46');
INSERT INTO `tb_user` VALUES ('50', '13304162069', 'e10adc3949ba59abbe56e057f20f883e', '2019-08-02 16:43:46', '2019-08-02 16:43:46');
INSERT INTO `tb_user` VALUES ('51', '13228119966', 'e10adc3949ba59abbe56e057f20f883e', '2019-08-02 16:43:46', '2019-08-02 16:43:46');
INSERT INTO `tb_user` VALUES ('52', '13303326951', 'e10adc3949ba59abbe56e057f20f883e', '2019-08-02 16:43:46', '2019-08-02 16:43:46');
INSERT INTO `tb_user` VALUES ('53', '13949043386', 'e10adc3949ba59abbe56e057f20f883e', '2019-08-02 16:43:46', '2019-08-02 16:43:46');
INSERT INTO `tb_user` VALUES ('54', '13117484024', 'e10adc3949ba59abbe56e057f20f883e', '2019-08-02 16:43:46', '2019-08-02 16:43:46');
INSERT INTO `tb_user` VALUES ('55', '13468570608', 'e10adc3949ba59abbe56e057f20f883e', '2019-08-02 16:43:46', '2019-08-02 16:43:46');
INSERT INTO `tb_user` VALUES ('56', '13829424512', 'e10adc3949ba59abbe56e057f20f883e', '2019-08-02 16:43:46', '2019-08-02 16:43:46');
INSERT INTO `tb_user` VALUES ('57', '13667416111', 'e10adc3949ba59abbe56e057f20f883e', '2019-08-02 16:43:46', '2019-08-02 16:43:46');
INSERT INTO `tb_user` VALUES ('58', '13107800128', 'e10adc3949ba59abbe56e057f20f883e', '2019-08-02 16:43:46', '2019-08-02 16:43:46');
INSERT INTO `tb_user` VALUES ('59', '13456540976', 'e10adc3949ba59abbe56e057f20f883e', '2019-08-02 16:43:46', '2019-08-02 16:43:46');
INSERT INTO `tb_user` VALUES ('60', '13986961800', 'e10adc3949ba59abbe56e057f20f883e', '2019-08-02 16:43:46', '2019-08-02 16:43:46');
INSERT INTO `tb_user` VALUES ('61', '13471803074', 'e10adc3949ba59abbe56e057f20f883e', '2019-08-02 16:43:46', '2019-08-02 16:43:46');
INSERT INTO `tb_user` VALUES ('62', '13291235699', 'e10adc3949ba59abbe56e057f20f883e', '2019-08-02 16:43:46', '2019-08-02 16:43:46');
INSERT INTO `tb_user` VALUES ('63', '13165654879', 'e10adc3949ba59abbe56e057f20f883e', '2019-08-02 16:43:46', '2019-08-02 16:43:46');
INSERT INTO `tb_user` VALUES ('64', '13160861931', 'e10adc3949ba59abbe56e057f20f883e', '2019-08-02 16:43:46', '2019-08-02 16:43:46');
INSERT INTO `tb_user` VALUES ('65', '13387208557', 'e10adc3949ba59abbe56e057f20f883e', '2019-08-02 16:43:46', '2019-08-02 16:43:46');
INSERT INTO `tb_user` VALUES ('66', '13021534882', 'e10adc3949ba59abbe56e057f20f883e', '2019-08-02 16:43:46', '2019-08-02 16:43:46');
INSERT INTO `tb_user` VALUES ('67', '13025422987', 'e10adc3949ba59abbe56e057f20f883e', '2019-08-02 16:43:46', '2019-08-02 16:43:46');
INSERT INTO `tb_user` VALUES ('68', '13536312434', 'e10adc3949ba59abbe56e057f20f883e', '2019-08-02 16:43:46', '2019-08-02 16:43:46');
INSERT INTO `tb_user` VALUES ('69', '13935547569', 'e10adc3949ba59abbe56e057f20f883e', '2019-08-02 16:43:46', '2019-08-02 16:43:46');
INSERT INTO `tb_user` VALUES ('70', '13936197370', 'e10adc3949ba59abbe56e057f20f883e', '2019-08-02 16:43:46', '2019-08-02 16:43:46');
INSERT INTO `tb_user` VALUES ('71', '13376796578', 'e10adc3949ba59abbe56e057f20f883e', '2019-08-02 16:43:46', '2019-08-02 16:43:46');
INSERT INTO `tb_user` VALUES ('72', '13773395735', 'e10adc3949ba59abbe56e057f20f883e', '2019-08-02 16:43:46', '2019-08-02 16:43:46');
INSERT INTO `tb_user` VALUES ('73', '13312354710', 'e10adc3949ba59abbe56e057f20f883e', '2019-08-02 16:43:46', '2019-08-02 16:43:46');
INSERT INTO `tb_user` VALUES ('74', '13206863662', 'e10adc3949ba59abbe56e057f20f883e', '2019-08-02 16:43:46', '2019-08-02 16:43:46');
INSERT INTO `tb_user` VALUES ('75', '13041697373', 'e10adc3949ba59abbe56e057f20f883e', '2019-08-02 16:43:46', '2019-08-02 16:43:46');
INSERT INTO `tb_user` VALUES ('76', '13274664831', 'e10adc3949ba59abbe56e057f20f883e', '2019-08-02 16:43:46', '2019-08-02 16:43:46');
INSERT INTO `tb_user` VALUES ('77', '13011170045', 'e10adc3949ba59abbe56e057f20f883e', '2019-08-02 16:43:46', '2019-08-02 16:43:46');
INSERT INTO `tb_user` VALUES ('78', '13439410129', 'e10adc3949ba59abbe56e057f20f883e', '2019-08-02 16:43:46', '2019-08-02 16:43:46');
INSERT INTO `tb_user` VALUES ('79', '13374944361', 'e10adc3949ba59abbe56e057f20f883e', '2019-08-02 16:43:46', '2019-08-02 16:43:46');
INSERT INTO `tb_user` VALUES ('80', '13876020928', 'e10adc3949ba59abbe56e057f20f883e', '2019-08-02 16:43:46', '2019-08-02 16:43:46');
INSERT INTO `tb_user` VALUES ('81', '13783165166', 'e10adc3949ba59abbe56e057f20f883e', '2019-08-02 16:43:46', '2019-08-02 16:43:46');
INSERT INTO `tb_user` VALUES ('82', '13278257374', 'e10adc3949ba59abbe56e057f20f883e', '2019-08-02 16:43:46', '2019-08-02 16:43:46');
INSERT INTO `tb_user` VALUES ('83', '13932855668', 'e10adc3949ba59abbe56e057f20f883e', '2019-08-02 16:43:46', '2019-08-02 16:43:46');
INSERT INTO `tb_user` VALUES ('84', '13861882191', 'e10adc3949ba59abbe56e057f20f883e', '2019-08-02 16:43:46', '2019-08-02 16:43:46');
INSERT INTO `tb_user` VALUES ('85', '13789549699', 'e10adc3949ba59abbe56e057f20f883e', '2019-08-02 16:43:46', '2019-08-02 16:43:46');
INSERT INTO `tb_user` VALUES ('86', '13762048705', 'e10adc3949ba59abbe56e057f20f883e', '2019-08-02 16:43:46', '2019-08-02 16:43:46');
INSERT INTO `tb_user` VALUES ('87', '13816921897', 'e10adc3949ba59abbe56e057f20f883e', '2019-08-02 16:43:46', '2019-08-02 16:43:46');
INSERT INTO `tb_user` VALUES ('88', '13790600059', 'e10adc3949ba59abbe56e057f20f883e', '2019-08-02 16:43:46', '2019-08-02 16:43:46');
INSERT INTO `tb_user` VALUES ('89', '13501033423', 'e10adc3949ba59abbe56e057f20f883e', '2019-08-02 16:43:46', '2019-08-02 16:43:46');
INSERT INTO `tb_user` VALUES ('90', '13185229233', 'e10adc3949ba59abbe56e057f20f883e', '2019-08-02 16:43:46', '2019-08-02 16:43:46');
INSERT INTO `tb_user` VALUES ('91', '13867747315', 'e10adc3949ba59abbe56e057f20f883e', '2019-08-02 16:43:46', '2019-08-02 16:43:46');
INSERT INTO `tb_user` VALUES ('92', '13201151587', 'e10adc3949ba59abbe56e057f20f883e', '2019-08-02 16:43:46', '2019-08-02 16:43:46');
INSERT INTO `tb_user` VALUES ('93', '13992764840', 'e10adc3949ba59abbe56e057f20f883e', '2019-08-02 16:43:46', '2019-08-02 16:43:46');
INSERT INTO `tb_user` VALUES ('94', '13162800744', 'e10adc3949ba59abbe56e057f20f883e', '2019-08-02 16:43:46', '2019-08-02 16:43:46');
INSERT INTO `tb_user` VALUES ('95', '13626499286', 'e10adc3949ba59abbe56e057f20f883e', '2019-08-02 16:43:46', '2019-08-02 16:43:46');
INSERT INTO `tb_user` VALUES ('96', '13790972847', 'e10adc3949ba59abbe56e057f20f883e', '2019-08-02 16:43:46', '2019-08-02 16:43:46');
INSERT INTO `tb_user` VALUES ('97', '13806542590', 'e10adc3949ba59abbe56e057f20f883e', '2019-08-02 16:43:46', '2019-08-02 16:43:46');
INSERT INTO `tb_user` VALUES ('98', '13605370098', 'e10adc3949ba59abbe56e057f20f883e', '2019-08-02 16:43:46', '2019-08-02 16:43:46');
INSERT INTO `tb_user` VALUES ('99', '13745677232', 'e10adc3949ba59abbe56e057f20f883e', '2019-08-02 16:43:46', '2019-08-02 16:43:46');
INSERT INTO `tb_user` VALUES ('100', '17602026969', 'e10adc3949ba59abbe56e057f20f883e', '2019-09-26 20:01:13', '2019-09-26 20:01:13');
INSERT INTO `tb_user` VALUES ('101', '17602026961', 'e10adc3949ba59abbe56e057f20f883e', '2019-09-26 20:03:21', '2019-09-26 20:03:21');
INSERT INTO `tb_user` VALUES ('102', '17602026962', 'e10adc3949ba59abbe56e057f20f883e', '2019-10-16 11:38:52', '2019-10-16 11:38:52');
INSERT INTO `tb_user` VALUES ('103', '17602189189', 'e10adc3949ba59abbe56e057f20f883e', '2019-10-23 11:40:37', '2019-10-23 11:40:37');

-- ----------------------------
-- Table structure for tb_user_info
-- ----------------------------
DROP TABLE IF EXISTS `tb_user_info`;
CREATE TABLE `tb_user_info` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `user_id` bigint(20) NOT NULL COMMENT '用户id',
  `nick_name` varchar(50) DEFAULT NULL COMMENT '昵称',
  `logo` varchar(100) DEFAULT NULL COMMENT '用户头像',
  `tags` varchar(50) DEFAULT NULL COMMENT '用户标签：多个用逗号分隔',
  `sex` int(1) DEFAULT '3' COMMENT '性别，1-男，2-女，3-未知',
  `age` int(11) DEFAULT NULL COMMENT '用户年龄',
  `edu` varchar(20) DEFAULT NULL COMMENT '学历',
  `city` varchar(20) DEFAULT NULL COMMENT '居住城市',
  `birthday` varchar(20) DEFAULT NULL COMMENT '生日',
  `cover_pic` varchar(100) DEFAULT NULL COMMENT '封面图片',
  `industry` varchar(20) DEFAULT NULL COMMENT '行业',
  `income` varchar(20) DEFAULT NULL COMMENT '收入',
  `marriage` varchar(20) DEFAULT NULL COMMENT '婚姻状态',
  `created` datetime DEFAULT NULL,
  `updated` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `user_id` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=102 DEFAULT CHARSET=utf8 COMMENT='用户信息表';

-- ----------------------------
-- Records of tb_user_info
-- ----------------------------
INSERT INTO `tb_user_info` VALUES ('1', '1', 'heima', 'http://xfjy.oss-cn-shanghai.aliyuncs.com/images/2019/10/30/15724146111289758.jpg', '单身,本科,年龄相仿', '2', '30', '本科', '北京市-北京城区-东城区', '2019-09-30', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/21.jpg', '计算机行业', '30', '已婚', '2019-08-02 16:44:23', '2019-10-30 13:50:11');
INSERT INTO `tb_user_info` VALUES ('2', '2', 'heima_2', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/22.jpg', '单身,本科,年龄相仿', '1', '30', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/22.jpg', '计算机行业', '30', '已婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('3', '3', 'heima_3', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/18.jpg', '单身,本科,年龄相仿', '1', '45', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/18.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('4', '4', 'heima_4', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/5.jpg', '单身,本科,年龄相仿', '1', '34', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/5.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('5', '5', 'heima_5', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/16.jpg', '单身,本科,年龄相仿', '1', '23', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/16.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('6', '6', 'heima_6', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/2.jpg', '单身,本科,年龄相仿', '1', '49', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/2.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('7', '7', 'heima_7', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/11.jpg', '单身,本科,年龄相仿', '1', '43', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/11.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('8', '8', 'heima_8', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/16.jpg', '单身,本科,年龄相仿', '1', '26', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/16.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('9', '9', 'heima_9', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/2.jpg', '单身,本科,年龄相仿', '1', '43', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/2.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('10', '10', 'heima_10', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/2.jpg', '单身,本科,年龄相仿', '1', '45', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/2.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('11', '11', 'heima_11', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/5.jpg', '单身,本科,年龄相仿', '1', '38', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/5.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('12', '12', 'heima_12', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/19.jpg', '单身,本科,年龄相仿', '1', '49', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/19.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('13', '13', 'heima_13', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/13.jpg', '单身,本科,年龄相仿', '2', '36', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/13.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('14', '14', 'heima_14', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/18.jpg', '单身,本科,年龄相仿', '1', '21', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/18.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('15', '15', 'heima_15', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/17.jpg', '单身,本科,年龄相仿', '1', '48', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/17.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('16', '16', 'heima_16', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/13.jpg', '单身,本科,年龄相仿', '1', '34', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/13.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('17', '17', 'heima_17', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/17.jpg', '单身,本科,年龄相仿', '1', '47', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/17.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('18', '18', 'heima_18', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/15.jpg', '单身,本科,年龄相仿', '1', '20', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/15.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('19', '19', 'heima_19', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/2.jpg', '单身,本科,年龄相仿', '1', '48', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/2.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('20', '20', 'heima_20', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/6.jpg', '单身,本科,年龄相仿', '1', '21', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/6.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('21', '21', 'heima_21', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/15.jpg', '单身,本科,年龄相仿', '1', '30', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/15.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('22', '22', 'heima_22', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/10.jpg', '单身,本科,年龄相仿', '1', '49', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/10.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('23', '23', 'heima_23', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/19.jpg', '单身,本科,年龄相仿', '1', '27', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/19.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('24', '24', 'heima_24', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/2.jpg', '单身,本科,年龄相仿', '1', '35', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/2.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('25', '25', 'heima_25', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/18.jpg', '单身,本科,年龄相仿', '1', '38', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/18.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('26', '26', 'heima_26', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/18.jpg', '单身,本科,年龄相仿', '1', '35', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/18.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('27', '27', 'heima_27', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/7.jpg', '单身,本科,年龄相仿', '1', '45', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/7.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('28', '28', 'heima_28', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/15.jpg', '单身,本科,年龄相仿', '1', '37', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/15.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('29', '29', 'heima_29', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/7.jpg', '单身,本科,年龄相仿', '1', '40', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/7.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('30', '30', 'heima_30', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/8.jpg', '单身,本科,年龄相仿', '1', '49', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/8.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('31', '31', 'heima_31', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/4.jpg', '单身,本科,年龄相仿', '1', '37', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/4.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('32', '32', 'heima_32', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/10.jpg', '单身,本科,年龄相仿', '1', '44', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/10.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('33', '33', 'heima_33', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/13.jpg', '单身,本科,年龄相仿', '1', '26', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/13.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('34', '34', 'heima_34', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/13.jpg', '单身,本科,年龄相仿', '1', '22', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/13.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('35', '35', 'heima_35', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/2.jpg', '单身,本科,年龄相仿', '1', '35', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/2.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('36', '36', 'heima_36', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/6.jpg', '单身,本科,年龄相仿', '1', '26', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/6.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('37', '37', 'heima_37', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/19.jpg', '单身,本科,年龄相仿', '1', '28', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/19.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('38', '38', 'heima_38', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/12.jpg', '单身,本科,年龄相仿', '1', '48', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/12.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('39', '39', 'heima_39', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/1.jpg', '单身,本科,年龄相仿', '1', '42', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/1.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('40', '40', 'heima_40', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/5.jpg', '单身,本科,年龄相仿', '1', '21', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/5.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('41', '41', 'heima_41', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/12.jpg', '单身,本科,年龄相仿', '1', '25', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/12.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('42', '42', 'heima_42', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/19.jpg', '单身,本科,年龄相仿', '1', '41', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/19.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('43', '43', 'heima_43', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/19.jpg', '单身,本科,年龄相仿', '1', '41', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/19.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('44', '44', 'heima_44', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/5.jpg', '单身,本科,年龄相仿', '1', '46', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/5.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('45', '45', 'heima_45', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/13.jpg', '单身,本科,年龄相仿', '1', '39', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/13.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('46', '46', 'heima_46', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/3.jpg', '单身,本科,年龄相仿', '1', '27', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/3.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('47', '47', 'heima_47', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/15.jpg', '单身,本科,年龄相仿', '1', '45', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/15.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('48', '48', 'heima_48', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/9.jpg', '单身,本科,年龄相仿', '1', '25', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/9.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('49', '49', 'heima_49', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/9.jpg', '单身,本科,年龄相仿', '1', '36', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/9.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('50', '50', 'heima_50', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/6.jpg', '单身,本科,年龄相仿', '1', '23', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/6.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('51', '51', 'heima_51', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/8.jpg', '单身,本科,年龄相仿', '1', '42', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/8.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('52', '52', 'heima_52', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/11.jpg', '单身,本科,年龄相仿', '1', '26', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/11.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('53', '53', 'heima_53', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/12.jpg', '单身,本科,年龄相仿', '1', '23', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/12.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('54', '54', 'heima_54', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/12.jpg', '单身,本科,年龄相仿', '1', '37', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/12.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('55', '55', 'heima_55', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/3.jpg', '单身,本科,年龄相仿', '1', '38', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/3.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('56', '56', 'heima_56', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/15.jpg', '单身,本科,年龄相仿', '1', '40', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/15.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('57', '57', 'heima_57', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/4.jpg', '单身,本科,年龄相仿', '1', '40', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/4.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('58', '58', 'heima_58', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/7.jpg', '单身,本科,年龄相仿', '1', '33', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/7.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('59', '59', 'heima_59', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/13.jpg', '单身,本科,年龄相仿', '1', '29', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/13.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('60', '60', 'heima_60', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/10.jpg', '单身,本科,年龄相仿', '1', '21', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/10.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('61', '61', 'heima_61', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/16.jpg', '单身,本科,年龄相仿', '1', '49', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/16.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('62', '62', 'heima_62', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/4.jpg', '单身,本科,年龄相仿', '1', '47', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/4.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('63', '63', 'heima_63', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/9.jpg', '单身,本科,年龄相仿', '1', '35', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/9.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('64', '64', 'heima_64', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/1.jpg', '单身,本科,年龄相仿', '1', '25', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/1.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('65', '65', 'heima_65', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/13.jpg', '单身,本科,年龄相仿', '1', '43', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/13.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('66', '66', 'heima_66', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/16.jpg', '单身,本科,年龄相仿', '1', '28', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/16.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('67', '67', 'heima_67', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/2.jpg', '单身,本科,年龄相仿', '1', '37', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/2.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('68', '68', 'heima_68', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/9.jpg', '单身,本科,年龄相仿', '1', '36', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/9.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('69', '69', 'heima_69', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/8.jpg', '单身,本科,年龄相仿', '1', '30', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/8.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('70', '70', 'heima_70', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/11.jpg', '单身,本科,年龄相仿', '1', '24', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/11.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('71', '71', 'heima_71', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/10.jpg', '单身,本科,年龄相仿', '1', '48', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/10.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('72', '72', 'heima_72', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/3.jpg', '单身,本科,年龄相仿', '1', '47', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/3.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('73', '73', 'heima_73', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/6.jpg', '单身,本科,年龄相仿', '1', '43', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/6.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('74', '74', 'heima_74', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/14.jpg', '单身,本科,年龄相仿', '1', '42', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/14.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('75', '75', 'heima_75', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/1.jpg', '单身,本科,年龄相仿', '1', '37', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/1.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('76', '76', 'heima_76', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/6.jpg', '单身,本科,年龄相仿', '1', '24', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/6.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('77', '77', 'heima_77', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/10.jpg', '单身,本科,年龄相仿', '1', '23', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/10.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('78', '78', 'heima_78', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/12.jpg', '单身,本科,年龄相仿', '1', '38', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/12.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('79', '79', 'heima_79', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/3.jpg', '单身,本科,年龄相仿', '1', '20', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/3.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('80', '80', 'heima_80', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/17.jpg', '单身,本科,年龄相仿', '1', '47', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/17.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('81', '81', 'heima_81', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/6.jpg', '单身,本科,年龄相仿', '1', '41', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/6.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('82', '82', 'heima_82', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/8.jpg', '单身,本科,年龄相仿', '1', '41', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/8.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('83', '83', 'heima_83', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/12.jpg', '单身,本科,年龄相仿', '1', '41', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/12.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('84', '84', 'heima_84', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/15.jpg', '单身,本科,年龄相仿', '1', '43', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/15.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('85', '85', 'heima_85', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/19.jpg', '单身,本科,年龄相仿', '1', '38', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/19.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('86', '86', 'heima_86', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/15.jpg', '单身,本科,年龄相仿', '1', '34', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/15.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('87', '87', 'heima_87', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/9.jpg', '单身,本科,年龄相仿', '1', '29', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/9.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('88', '88', 'heima_88', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/6.jpg', '单身,本科,年龄相仿', '1', '34', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/6.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('89', '89', 'heima_89', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/19.jpg', '单身,本科,年龄相仿', '1', '34', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/19.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('90', '90', 'heima_90', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/14.jpg', '单身,本科,年龄相仿', '1', '45', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/14.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('91', '91', 'heima_91', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/4.jpg', '单身,本科,年龄相仿', '1', '27', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/4.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('92', '92', 'heima_92', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/2.jpg', '单身,本科,年龄相仿', '1', '45', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/2.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('93', '93', 'heima_93', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/19.jpg', '单身,本科,年龄相仿', '1', '30', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/19.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('94', '94', 'heima_94', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/8.jpg', '单身,本科,年龄相仿', '1', '48', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/8.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('95', '95', 'heima_95', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/11.jpg', '单身,本科,年龄相仿', '1', '32', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/11.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('96', '96', 'heima_96', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/19.jpg', '单身,本科,年龄相仿', '1', '41', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/19.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('97', '97', 'heima_97', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/12.jpg', '单身,本科,年龄相仿', '1', '23', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/12.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('98', '102', 'heima_98', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/7.jpg', '单身,本科,年龄相仿', '1', '28', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/7.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('99', '99', 'heima_99', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/9.jpg', '单身,本科,年龄相仿', '1', '29', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/9.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('100', '100', '波仔', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/9.jpg', null, '1', '20', null, '北京市-北京城区-东城区', '2019-09-26', null, null, null, null, '2019-09-26 20:01:20', '2019-09-26 20:01:20');
INSERT INTO `tb_user_info` VALUES ('101', '101', '波仔', 'https://xfjy.oss-cn-shanghai.aliyuncs.com/images/logo/12.jpg', null, '1', '21', null, '北京市-北京城区-东城区', '2019-09-26', null, null, null, null, '2019-09-26 20:03:27', '2019-09-26 20:03:27');

-- =============================================
-- 运营后台管理表（v2.0 新增）
-- =============================================

-- 用户表新增状态字段（封禁/解封）
ALTER TABLE `tb_user` ADD COLUMN `status` varchar(10) DEFAULT 'NORMAL' COMMENT '状态：NORMAL-正常，FROZEN-已封禁' AFTER `password`;
ALTER TABLE `tb_user` ADD COLUMN `freeze_reason` varchar(200) DEFAULT NULL COMMENT '封禁原因' AFTER `status`;

-- ----------------------------
-- Table structure for tb_admin（管理员表）
-- ----------------------------
DROP TABLE IF EXISTS `tb_admin`;
CREATE TABLE `tb_admin` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `username` varchar(50) NOT NULL COMMENT '登录账号',
  `password` varchar(32) NOT NULL COMMENT '密码（MD5）',
  `nickname` varchar(50) DEFAULT NULL COMMENT '昵称',
  `avatar` varchar(200) DEFAULT NULL COMMENT '头像',
  `role` varchar(20) DEFAULT 'ADMIN' COMMENT '角色：SUPER_ADMIN-超级管理员，ADMIN-管理员，OPERATOR-运营',
  `status` varchar(10) DEFAULT 'NORMAL' COMMENT '状态：NORMAL-正常，DISABLED-已禁用',
  `created` datetime DEFAULT NULL,
  `updated` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='管理员表';

-- 初始管理员：admin / admin123（MD5: 0192023a7bbd73250516f069df18b500）
INSERT INTO `tb_admin` VALUES ('1', 'admin', '0192023a7bbd73250516f069df18b500', '超级管理员', NULL, 'SUPER_ADMIN', 'NORMAL', NOW(), NOW());

-- ----------------------------
-- Table structure for tb_notice（公告表-后台管理）
-- ----------------------------
DROP TABLE IF EXISTS `tb_notice`;
CREATE TABLE `tb_notice` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `title` varchar(100) NOT NULL COMMENT '标题',
  `content` text COMMENT '内容',
  `type` varchar(20) DEFAULT 'SYSTEM' COMMENT '类型：SYSTEM-系统公告，ACTIVITY-活动公告，UPDATE-更新公告',
  `status` varchar(20) DEFAULT 'DRAFT' COMMENT '状态：DRAFT-草稿，PUBLISHED-已发布，OFFLINE-已下线',
  `publisher` varchar(50) DEFAULT NULL COMMENT '发布人',
  `published_at` datetime DEFAULT NULL COMMENT '发布时间',
  `created` datetime DEFAULT NULL,
  `updated` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='公告表（后台管理）';

-- ----------------------------
-- Table structure for tb_review（内容审核表）
-- ----------------------------
DROP TABLE IF EXISTS `tb_review`;
CREATE TABLE `tb_review` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `type` varchar(20) DEFAULT 'POST' COMMENT '类型：POST-动态，PHOTO-图片，VIDEO-视频',
  `user_id` bigint(20) NOT NULL COMMENT '发布者用户ID',
  `content` text COMMENT '内容',
  `medias` varchar(1000) DEFAULT NULL COMMENT '媒体文件URL，逗号分隔',
  `status` varchar(20) DEFAULT 'PENDING' COMMENT '状态：PENDING-待审核，APPROVED-已通过，REJECTED-已驳回',
  `reject_reason` varchar(200) DEFAULT NULL COMMENT '驳回原因',
  `reviewer` varchar(50) DEFAULT NULL COMMENT '审核人',
  `reviewed_at` datetime DEFAULT NULL COMMENT '审核时间',
  `created` datetime DEFAULT NULL,
  `updated` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `user_id` (`user_id`),
  KEY `status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='内容审核表';

-- ----------------------------
-- Table structure for tb_report（举报表）
-- ----------------------------
DROP TABLE IF EXISTS `tb_report`;
CREATE TABLE `tb_report` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `reporter_id` bigint(20) NOT NULL COMMENT '举报人用户ID',
  `target_user_id` bigint(20) NOT NULL COMMENT '被举报用户ID',
  `target_type` varchar(20) DEFAULT 'USER' COMMENT '举报对象类型：USER-用户，POST-动态，COMMENT-评论，MESSAGE-私信',
  `target_id` bigint(20) DEFAULT NULL COMMENT '举报对象ID',
  `reason` varchar(100) NOT NULL COMMENT '举报原因',
  `description` text COMMENT '详细描述',
  `evidence` varchar(1000) DEFAULT NULL COMMENT '证据截图URL，逗号分隔',
  `status` varchar(20) DEFAULT 'PENDING' COMMENT '状态：PENDING-待处理，PROCESSING-处理中，RESOLVED-已处理，DISMISSED-已驳回',
  `handler` varchar(50) DEFAULT NULL COMMENT '处理人',
  `handled_at` datetime DEFAULT NULL COMMENT '处理时间',
  `handle_result` varchar(200) DEFAULT NULL COMMENT '处理结果备注',
  `created` datetime DEFAULT NULL,
  `updated` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `reporter_id` (`reporter_id`),
  KEY `target_user_id` (`target_user_id`),
  KEY `status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='举报表';

-- ----------------------------
-- Table structure for tb_vip_package（VIP 套餐表，pay-service 独占）
-- ----------------------------
DROP TABLE IF EXISTS `tb_vip_package`;
CREATE TABLE `tb_vip_package` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `name` varchar(50) NOT NULL COMMENT '套餐名称（月卡/季卡/年卡）',
  `level` int(11) NOT NULL COMMENT 'VIP 等级：1-月卡会员，2-季卡会员，3-年卡会员',
  `price` bigint(20) NOT NULL COMMENT '售价，单位：分',
  `original_price` bigint(20) DEFAULT NULL COMMENT '原价，单位：分',
  `duration_days` int(11) NOT NULL COMMENT '有效天数（30/90/365）',
  `description` varchar(200) DEFAULT NULL COMMENT '套餐描述',
  `status` int(1) DEFAULT '1' COMMENT '状态：1-上架，0-下架',
  `sort` int(11) DEFAULT '0' COMMENT '排序权重，越小越靠前',
  `created` datetime DEFAULT NULL,
  `updated` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='VIP 套餐表';

INSERT INTO `tb_vip_package` VALUES ('1', '月卡', 1, 3000, 4500, 30, '月度会员，每日优先推荐位', 1, 1, NOW(), NOW());
INSERT INTO `tb_vip_package` VALUES ('2', '季卡', 2, 7800, 13500, 90, '季度会员，立省12元，专属标识', 1, 2, NOW(), NOW());
INSERT INTO `tb_vip_package` VALUES ('3', '年卡', 3, 25800, 54000, 365, '年度会员，立省102元，全部特权', 1, 3, NOW(), NOW());

-- ----------------------------
-- Table structure for tb_pay_order（支付订单表，pay-service 独占）
-- ----------------------------
DROP TABLE IF EXISTS `tb_pay_order`;
CREATE TABLE `tb_pay_order` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `order_no` varchar(32) NOT NULL COMMENT '订单号（VIP+yyyyMMdd+6位序列）',
  `user_id` bigint(20) NOT NULL COMMENT '下单用户ID',
  `package_id` bigint(20) NOT NULL COMMENT '套餐ID',
  `package_name` varchar(50) DEFAULT NULL COMMENT '套餐名称（冗余）',
  `amount` bigint(20) NOT NULL COMMENT '支付金额，单位：分',
  `status` varchar(20) DEFAULT 'PENDING' COMMENT '状态：PENDING-待支付，PAID-已支付，EXPIRED-已过期，CANCELLED-已取消',
  `pay_type` varchar(20) DEFAULT 'SANDBOX' COMMENT '支付方式：SANDBOX-模拟沙箱，ALIPAY-支付宝，WECHAT-微信',
  `paid_time` datetime DEFAULT NULL COMMENT '实际支付时间',
  `expire_time` datetime DEFAULT NULL COMMENT '订单过期时间（下单后30分钟）',
  `created` datetime DEFAULT NULL,
  `updated` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `user_id` (`user_id`),
  KEY `status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='支付订单表';

-- ----------------------------
-- Table structure for tb_user_vip（用户VIP记录表，pay-service 独占）
-- ----------------------------
DROP TABLE IF EXISTS `tb_user_vip`;
CREATE TABLE `tb_user_vip` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `level` int(11) DEFAULT '0' COMMENT 'VIP等级：0-非会员，1-月卡，2-季卡，3-年卡',
  `expire_time` datetime DEFAULT NULL COMMENT 'VIP到期时间',
  `source` varchar(20) DEFAULT 'PAY' COMMENT '开通来源：PAY-支付开通，GIFT-运营赠送',
  `created` datetime DEFAULT NULL,
  `updated` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='用户VIP记录表';
