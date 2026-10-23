---
name: 密钥禁止入库约束
description: 用户要求项目密钥/凭证不能上传到仓库，必须环境变量外置+gitignore+example模板
type: feedback
---

项目的密钥key不能上传到仓库，敏感信息必须写约束。

**Why:** 用户明确要求（2026-10-23）。发现aliyun.properties含真实阿里云AccessKey已入git历史，属于凭证泄露风险。
**How to apply:**
- 生成配置文件时密码/密钥必须用`${ENV_VAR:default}`环境变量占位符，禁止硬编码明文
- 含凭证的文件不入库，提供`.example`模板+加入.gitignore
- `**/aliyun.properties`、`**/application-local.yml`、`**/*secret*`等已在.gitignore中
- 详细规则见`.joycode/rules/security-no-secrets.md`
- 阿里云AccessKey需在控制台轮换（旧Key在git历史中仍可检索）