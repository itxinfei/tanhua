-- 心飞交友 MySQL 初始化（容器首次启动自动执行）
-- 建库 + 建业务账号（表结构由 setup.sh 导入 scripts/mytanhua.sql）
CREATE DATABASE IF NOT EXISTS xfjy
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'xfjy'@'%' IDENTIFIED BY '123456';
GRANT ALL PRIVILEGES ON xfjy.* TO 'xfjy'@'%';
FLUSH PRIVILEGES;