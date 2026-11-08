-- 2026-11-08: 调整 unlimited_swipe 权益，月卡（VIP1）即可享受无限滑动
-- 背景：用户开通月卡后匹配页仍提示"开通VIP无限滑"，因原配置要求 VIP2（季卡）
-- 影响：仅修改 tb_value_added_service 表的 vip_level_required 字段
-- 回滚：UPDATE tb_value_added_service SET vip_level_required=2 WHERE service_code='unlimited_swipe';

UPDATE `tb_value_added_service`
SET `vip_level_required` = 1,
    `updated` = CURRENT_TIMESTAMP
WHERE `service_code` = 'unlimited_swipe';

-- 验证
SELECT service_code, name, vip_level_required, description
FROM `tb_value_added_service`
WHERE `service_code` = 'unlimited_swipe';
