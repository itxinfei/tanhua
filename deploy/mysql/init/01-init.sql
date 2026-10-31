-- 心飞交友 MySQL 初始化（容器首次启动自动执行，按文件名排序）
-- 01 建库 + 业务账号；02/03/04 由 scripts/sync-mysql-init.ps1 同步生成，自动建表与种子数据
CREATE DATABASE IF NOT EXISTS xfjy
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'xfjy'@'%' IDENTIFIED BY '123456';
GRANT ALL PRIVILEGES ON xfjy.* TO 'xfjy'@'%';
FLUSH PRIVILEGES;