# xfjy-admin — 心飞交友管理后台

心飞交友平台的管理端 Web 应用，供运营人员执行内容审核、举报处理、用户管理、公告发布与数据查看。

## 技术栈

| 项 | 技术 |
|----|------|
| 框架 | Vue 3.5 + TypeScript |
| UI | Element Plus 2.14 |
| 图表 | ECharts |
| 状态 | Pinia |
| 路由 | Vue Router 4 |
| 构建 | Vite 8 |

## 功能模块

| 模块 | 路由 | 说明 |
|------|------|------|
| 登录 | `/login` | 管理员账号密码登录（Sa-Token 鉴权） |
| 数据看板 | `/dashboard` | 用户/动态/举报核心指标与趋势图 |
| 内容审核 | `/review` | 动态/评论/视频审核（通过/驳回） |
| 举报管理 | `/report` | 用户举报工单处理（PENDING → 处理/驳回） |
| 公告管理 | `/notice` | 系统公告发布/编辑/下线 |
| 用户管理 | `/user` | 用户列表检索、详情查看、封禁 |

## 快速开始

```bash
# 安装依赖
npm install

# 开发模式（默认 5173 端口）
npm run dev

# 生产构建（vue-tsc 类型检查 + vite build）
npm run build
```

## 环境变量

开发环境默认通过 Vite 代理转发 API 到网关。如需调整后端地址，修改 `vite.config.ts` 中的 `proxy.target`。

## 接口约定

所有管理端接口以 `/admin/**` 为前缀，经 `xfjy-gateway` 路由到 `xfjy-server`，由 `AdminTokenInterceptor` 校验 Sa-Token。请求封装见 `src/api/request.ts`，统一携带 `Authorization: Bearer <token>`。