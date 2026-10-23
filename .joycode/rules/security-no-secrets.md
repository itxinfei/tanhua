# 安全约束：密钥与敏感信息禁止入库

> 本规则为项目级强制约束，所有开发者（含AI辅助编程）必须遵守。

## 一、禁止提交的敏感信息类型

1. **云平台凭证**：AccessKeyId / AccessKeySecret（阿里云OSS等）
2. **数据库密码**：MySQL / MongoDB / Redis 密码
3. **第三方API凭证**：环信 client-id / client-secret、百度地图AK等
4. **证书与私钥**：*.pem / *.p12 / *.keystore 文件
5. **JWT签名密钥**、加密对称密钥

## 二、正确做法

### 1. 配置文件使用环境变量占位符

```yaml
# 正确 ✅ —— 环境变量注入，冒号后为本地开发回退默认值
password: ${MYSQL_PASSWORD:root}

# 错误 ❌ —— 硬编码明文密码
password: root
```

### 2. 真实凭证文件不入库，提供 .example 模板

- `aliyun.properties` → 已加入 .gitignore，仅提交 `aliyun.properties.example`（占位符）
- 新增含凭证的配置文件时，必须同时提供 `.example` 模板并将真实文件加入 .gitignore

### 3. application-local.yml 不入库

- 本地环境配置（可能含真实密码）已加入 .gitignore
- 提交到仓库的 application.yml / application-test.yml 必须使用 `${ENV_VAR:default}` 占位符

## 三、.gitignore 已覆盖的规则

```
**/aliyun.properties
**/application-local.yml
**/application-local.yaml
**/.env
**/.env.*
**/*.pem
**/*.p12
**/*.keystore
**/*.key
**/*.jks
**/*secret*
**/*credential*
**/secrets.yml
!.joycode/          # 例外：项目规则文档不受通配规则影响
!.joycode/**
```

## 四、AI辅助编程约束

- 生成配置文件时，密码/密钥字段必须使用 `${ENV_VAR:default}` 格式
- 不得在代码中硬编码任何凭证字符串
- 新增配置文件时检查是否含敏感信息，含则加 .gitignore + 提供 .example
- 发现已入库的敏感信息，立即 git rm --cached 并提醒用户轮换凭证

## 五、凭证泄露应急流程

1. 立即 `git rm --cached <文件>` 停止后续跟踪
2. 在云平台控制台**轮换（作废旧Key、生成新Key）**——git历史中旧Key仍可被检索
3. 如需彻底清除历史，使用 `git filter-repo` 或 BFG Repo-Cleaner（需团队协调）
4. 提交修复并通知所有协作者