## 改动内容

<!-- 一个 PR 只做一件事；说明改了什么、为什么改 -->

## 关联 Issue

## 验证结果

- [ ] 后端 `mvn -q -DskipTests compile` 通过（改动涉及后端时）
- [ ] `npm run build:h5` 与 `npm run type-check` 通过（改动涉及 `xfjy-app` 时）
- [ ] `npm run build` 与 `npm run lint:style` 通过（改动涉及 `xfjy-admin` 时）
- [ ] `pwsh scripts/sync-agent-rules.ps1 -Check` 通过（改动涉及规则文件时）
- [ ] 接口改动已附 curl 示例与响应字段说明

## 规则变更

<!-- 填「无」，或写明改了哪些规则、为什么。AGENTS.md 由人维护，AI 不得直接改 -->

## 数据与回滚

<!-- 涉及数据、配置或部署改动时，写明写入方与回滚方式；无改动填「无」 -->

## 自检

- [ ] 未提交 `.env`、密钥、口令、证书、临时脚本、截图产物
- [ ] 未使用 `git add -f` 绕过仓库白名单
- [ ] 提交信息符合 `type(scope): 中文简述`
