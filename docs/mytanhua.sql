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
INSERT INTO `tb_user_info` VALUES ('1', '1', 'heima', 'http://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/2019/10/30/15724146111289758.jpg', '单身,本科,年龄相仿', '2', '30', '本科', '北京市-北京城区-东城区', '2019-09-30', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/21.jpg', '计算机行业', '30', '已婚', '2019-08-02 16:44:23', '2019-10-30 13:50:11');
INSERT INTO `tb_user_info` VALUES ('2', '2', 'heima_2', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/22.jpg', '单身,本科,年龄相仿', '1', '30', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/22.jpg', '计算机行业', '30', '已婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('3', '3', 'heima_3', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/18.jpg', '单身,本科,年龄相仿', '1', '45', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/18.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('4', '4', 'heima_4', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/5.jpg', '单身,本科,年龄相仿', '1', '34', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/5.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('5', '5', 'heima_5', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/16.jpg', '单身,本科,年龄相仿', '1', '23', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/16.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('6', '6', 'heima_6', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/2.jpg', '单身,本科,年龄相仿', '1', '49', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/2.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('7', '7', 'heima_7', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/11.jpg', '单身,本科,年龄相仿', '1', '43', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/11.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('8', '8', 'heima_8', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/16.jpg', '单身,本科,年龄相仿', '1', '26', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/16.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('9', '9', 'heima_9', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/2.jpg', '单身,本科,年龄相仿', '1', '43', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/2.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('10', '10', 'heima_10', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/2.jpg', '单身,本科,年龄相仿', '1', '45', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/2.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('11', '11', 'heima_11', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/5.jpg', '单身,本科,年龄相仿', '1', '38', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/5.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('12', '12', 'heima_12', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/19.jpg', '单身,本科,年龄相仿', '1', '49', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/19.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('13', '13', 'heima_13', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/13.jpg', '单身,本科,年龄相仿', '2', '36', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/13.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('14', '14', 'heima_14', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/18.jpg', '单身,本科,年龄相仿', '1', '21', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/18.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('15', '15', 'heima_15', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/17.jpg', '单身,本科,年龄相仿', '1', '48', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/17.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('16', '16', 'heima_16', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/13.jpg', '单身,本科,年龄相仿', '1', '34', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/13.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('17', '17', 'heima_17', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/17.jpg', '单身,本科,年龄相仿', '1', '47', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/17.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('18', '18', 'heima_18', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/15.jpg', '单身,本科,年龄相仿', '1', '20', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/15.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('19', '19', 'heima_19', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/2.jpg', '单身,本科,年龄相仿', '1', '48', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/2.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('20', '20', 'heima_20', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/6.jpg', '单身,本科,年龄相仿', '1', '21', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/6.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('21', '21', 'heima_21', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/15.jpg', '单身,本科,年龄相仿', '1', '30', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/15.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('22', '22', 'heima_22', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/10.jpg', '单身,本科,年龄相仿', '1', '49', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/10.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('23', '23', 'heima_23', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/19.jpg', '单身,本科,年龄相仿', '1', '27', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/19.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('24', '24', 'heima_24', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/2.jpg', '单身,本科,年龄相仿', '1', '35', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/2.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('25', '25', 'heima_25', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/18.jpg', '单身,本科,年龄相仿', '1', '38', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/18.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('26', '26', 'heima_26', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/18.jpg', '单身,本科,年龄相仿', '1', '35', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/18.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('27', '27', 'heima_27', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/7.jpg', '单身,本科,年龄相仿', '1', '45', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/7.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('28', '28', 'heima_28', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/15.jpg', '单身,本科,年龄相仿', '1', '37', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/15.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('29', '29', 'heima_29', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/7.jpg', '单身,本科,年龄相仿', '1', '40', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/7.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('30', '30', 'heima_30', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/8.jpg', '单身,本科,年龄相仿', '1', '49', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/8.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('31', '31', 'heima_31', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/4.jpg', '单身,本科,年龄相仿', '1', '37', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/4.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('32', '32', 'heima_32', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/10.jpg', '单身,本科,年龄相仿', '1', '44', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/10.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('33', '33', 'heima_33', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/13.jpg', '单身,本科,年龄相仿', '1', '26', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/13.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('34', '34', 'heima_34', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/13.jpg', '单身,本科,年龄相仿', '1', '22', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/13.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('35', '35', 'heima_35', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/2.jpg', '单身,本科,年龄相仿', '1', '35', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/2.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('36', '36', 'heima_36', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/6.jpg', '单身,本科,年龄相仿', '1', '26', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/6.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('37', '37', 'heima_37', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/19.jpg', '单身,本科,年龄相仿', '1', '28', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/19.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('38', '38', 'heima_38', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/12.jpg', '单身,本科,年龄相仿', '1', '48', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/12.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('39', '39', 'heima_39', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/1.jpg', '单身,本科,年龄相仿', '1', '42', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/1.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('40', '40', 'heima_40', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/5.jpg', '单身,本科,年龄相仿', '1', '21', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/5.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('41', '41', 'heima_41', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/12.jpg', '单身,本科,年龄相仿', '1', '25', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/12.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('42', '42', 'heima_42', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/19.jpg', '单身,本科,年龄相仿', '1', '41', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/19.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('43', '43', 'heima_43', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/19.jpg', '单身,本科,年龄相仿', '1', '41', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/19.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('44', '44', 'heima_44', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/5.jpg', '单身,本科,年龄相仿', '1', '46', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/5.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('45', '45', 'heima_45', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/13.jpg', '单身,本科,年龄相仿', '1', '39', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/13.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('46', '46', 'heima_46', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/3.jpg', '单身,本科,年龄相仿', '1', '27', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/3.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('47', '47', 'heima_47', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/15.jpg', '单身,本科,年龄相仿', '1', '45', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/15.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('48', '48', 'heima_48', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/9.jpg', '单身,本科,年龄相仿', '1', '25', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/9.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('49', '49', 'heima_49', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/9.jpg', '单身,本科,年龄相仿', '1', '36', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/9.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('50', '50', 'heima_50', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/6.jpg', '单身,本科,年龄相仿', '1', '23', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/6.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('51', '51', 'heima_51', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/8.jpg', '单身,本科,年龄相仿', '1', '42', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/8.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('52', '52', 'heima_52', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/11.jpg', '单身,本科,年龄相仿', '1', '26', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/11.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('53', '53', 'heima_53', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/12.jpg', '单身,本科,年龄相仿', '1', '23', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/12.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('54', '54', 'heima_54', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/12.jpg', '单身,本科,年龄相仿', '1', '37', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/12.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('55', '55', 'heima_55', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/3.jpg', '单身,本科,年龄相仿', '1', '38', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/3.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('56', '56', 'heima_56', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/15.jpg', '单身,本科,年龄相仿', '1', '40', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/15.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('57', '57', 'heima_57', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/4.jpg', '单身,本科,年龄相仿', '1', '40', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/4.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('58', '58', 'heima_58', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/7.jpg', '单身,本科,年龄相仿', '1', '33', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/7.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('59', '59', 'heima_59', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/13.jpg', '单身,本科,年龄相仿', '1', '29', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/13.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('60', '60', 'heima_60', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/10.jpg', '单身,本科,年龄相仿', '1', '21', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/10.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('61', '61', 'heima_61', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/16.jpg', '单身,本科,年龄相仿', '1', '49', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/16.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('62', '62', 'heima_62', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/4.jpg', '单身,本科,年龄相仿', '1', '47', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/4.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('63', '63', 'heima_63', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/9.jpg', '单身,本科,年龄相仿', '1', '35', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/9.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('64', '64', 'heima_64', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/1.jpg', '单身,本科,年龄相仿', '1', '25', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/1.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('65', '65', 'heima_65', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/13.jpg', '单身,本科,年龄相仿', '1', '43', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/13.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('66', '66', 'heima_66', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/16.jpg', '单身,本科,年龄相仿', '1', '28', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/16.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('67', '67', 'heima_67', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/2.jpg', '单身,本科,年龄相仿', '1', '37', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/2.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('68', '68', 'heima_68', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/9.jpg', '单身,本科,年龄相仿', '1', '36', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/9.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('69', '69', 'heima_69', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/8.jpg', '单身,本科,年龄相仿', '1', '30', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/8.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('70', '70', 'heima_70', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/11.jpg', '单身,本科,年龄相仿', '1', '24', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/11.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('71', '71', 'heima_71', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/10.jpg', '单身,本科,年龄相仿', '1', '48', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/10.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('72', '72', 'heima_72', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/3.jpg', '单身,本科,年龄相仿', '1', '47', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/3.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('73', '73', 'heima_73', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/6.jpg', '单身,本科,年龄相仿', '1', '43', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/6.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('74', '74', 'heima_74', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/14.jpg', '单身,本科,年龄相仿', '1', '42', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/14.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('75', '75', 'heima_75', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/1.jpg', '单身,本科,年龄相仿', '1', '37', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/1.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('76', '76', 'heima_76', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/6.jpg', '单身,本科,年龄相仿', '1', '24', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/6.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('77', '77', 'heima_77', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/10.jpg', '单身,本科,年龄相仿', '1', '23', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/10.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('78', '78', 'heima_78', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/12.jpg', '单身,本科,年龄相仿', '1', '38', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/12.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('79', '79', 'heima_79', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/3.jpg', '单身,本科,年龄相仿', '1', '20', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/3.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('80', '80', 'heima_80', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/17.jpg', '单身,本科,年龄相仿', '1', '47', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/17.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('81', '81', 'heima_81', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/6.jpg', '单身,本科,年龄相仿', '1', '41', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/6.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('82', '82', 'heima_82', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/8.jpg', '单身,本科,年龄相仿', '1', '41', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/8.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('83', '83', 'heima_83', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/12.jpg', '单身,本科,年龄相仿', '1', '41', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/12.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('84', '84', 'heima_84', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/15.jpg', '单身,本科,年龄相仿', '1', '43', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/15.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('85', '85', 'heima_85', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/19.jpg', '单身,本科,年龄相仿', '1', '38', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/19.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('86', '86', 'heima_86', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/15.jpg', '单身,本科,年龄相仿', '1', '34', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/15.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('87', '87', 'heima_87', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/9.jpg', '单身,本科,年龄相仿', '1', '29', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/9.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('88', '88', 'heima_88', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/6.jpg', '单身,本科,年龄相仿', '1', '34', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/6.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('89', '89', 'heima_89', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/19.jpg', '单身,本科,年龄相仿', '1', '34', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/19.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('90', '90', 'heima_90', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/14.jpg', '单身,本科,年龄相仿', '1', '45', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/14.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('91', '91', 'heima_91', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/4.jpg', '单身,本科,年龄相仿', '1', '27', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/4.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('92', '92', 'heima_92', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/2.jpg', '单身,本科,年龄相仿', '1', '45', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/2.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('93', '93', 'heima_93', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/19.jpg', '单身,本科,年龄相仿', '1', '30', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/19.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('94', '94', 'heima_94', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/8.jpg', '单身,本科,年龄相仿', '1', '48', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/8.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('95', '95', 'heima_95', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/11.jpg', '单身,本科,年龄相仿', '1', '32', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/11.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('96', '96', 'heima_96', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/19.jpg', '单身,本科,年龄相仿', '1', '41', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/19.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('97', '97', 'heima_97', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/12.jpg', '单身,本科,年龄相仿', '1', '23', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/12.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('98', '102', 'heima_98', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/7.jpg', '单身,本科,年龄相仿', '1', '28', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/7.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('99', '99', 'heima_99', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/9.jpg', '单身,本科,年龄相仿', '1', '29', '本科', '北京市-北京城区-东城区', '2019-08-01', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/9.jpg', '计算机行业', '40', '未婚', '2019-08-02 16:44:23', '2019-08-02 16:44:23');
INSERT INTO `tb_user_info` VALUES ('100', '100', '波仔', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/9.jpg', null, '1', '20', null, '北京市-北京城区-东城区', '2019-09-26', null, null, null, null, '2019-09-26 20:01:20', '2019-09-26 20:01:20');
INSERT INTO `tb_user_info` VALUES ('101', '101', '波仔', 'https://itcast-tanhua.oss-cn-shanghai.aliyuncs.com/images/logo/12.jpg', null, '1', '21', null, '北京市-北京城区-东城区', '2019-09-26', null, null, null, null, '2019-09-26 20:03:27', '2019-09-26 20:03:27');
