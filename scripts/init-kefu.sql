-- ============================================================
-- 客服系统阶段1：FAQ 常见问题 + 意见反馈
-- 幂等导入（utf8mb4 避免中文乱码）：
--   mysql -u root -p --default-character-set=utf8mb4 xfjy < scripts/init-kefu.sql
-- ============================================================

-- ----------------------------
-- FAQ 常见问题表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `tb_faq` (
  `id`       BIGINT       NOT NULL AUTO_INCREMENT,
  `category` VARCHAR(50)  DEFAULT NULL COMMENT '分类',
  `question` VARCHAR(200) NOT NULL COMMENT '问题',
  `answer`   TEXT COMMENT '答案',
  `sort`     INT          DEFAULT 0 COMMENT '排序权重，越小越靠前',
  `status`   TINYINT      DEFAULT 1 COMMENT '1-启用 0-停用',
  `created`  DATETIME     DEFAULT CURRENT_TIMESTAMP,
  `updated`  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_question` (`question`),
  KEY `idx_status_sort` (`status`, `sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='常见问题FAQ';

-- FAQ 初始化（幂等：已存在则跳过）
INSERT INTO `tb_faq` (`category`, `question`, `answer`, `sort`) VALUES
  ('账号安全', '如何修改登录密码？', '进入「我的-设置-账号与安全」，点击修改密码，通过手机验证码即可重置。', 10),
  ('账号安全', '如何注销账号？', '进入「我的-设置-账号与安全-注销账号」，注销后数据将不可恢复，请谨慎操作。', 20),
  ('账号安全', '为什么要实名认证？', '为保障平台真实交友环境，部分功能需实名后使用，认证信息严格保密。', 30),
  ('会员权益', 'VIP 会员有什么特权？', 'VIP 会员享有超级喜欢、查看谁喜欢我、无限滑动、隐身访问、专属客服等权益，详见会员页权益矩阵。', 40),
  ('会员权益', '支付成功但会员未到账怎么办？', '请保留支付订单号，通过「我的-在线客服-意见反馈」提交，客服将在24小时内核实处理。', 50),
  ('匹配交友', '为什么推荐的人很少？', '完善个人资料、相册和实名认证可显著提升推荐量；也可使用「附近的人」主动发现。', 60),
  ('聊天互动', '为什么消息发送失败？', '消息可能触发敏感词拦截，请调整措辞后重试；若被对方拉黑也无法发送。', 70),
  ('举报投诉', '遇到骚扰或违规用户怎么办？', '在对方主页点击「举报」，选择举报类型提交，平台将在24小时内审核处理。', 80)
ON DUPLICATE KEY UPDATE `updated` = CURRENT_TIMESTAMP;

-- ----------------------------
-- 意见反馈表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `tb_feedback` (
  `id`      BIGINT       NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT       DEFAULT NULL COMMENT '提交用户ID',
  `content` TEXT COMMENT '反馈内容',
  `contact` VARCHAR(100) DEFAULT NULL COMMENT '联系方式（可选）',
  `reply`   TEXT COMMENT '客服回复',
  `status`  TINYINT      DEFAULT 0 COMMENT '0-待处理 1-已回复 2-已关闭',
  `created` DATETIME     DEFAULT CURRENT_TIMESTAMP,
  `updated` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='意见反馈';