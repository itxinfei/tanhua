# 心飞交友 Logo 资源包

品牌名：心飞交友（交友 App）
风格：探探风格 · 橙色圆角方形小鸟图标 + 黑色粗圆润字标
品牌主色：亮橙→深橙渐变（图标）；字标深黑（近 #1A1A1A）；脸部奶油米白
画幅：主标/图标 1:1（2048×2048），横版 3:1（3547×1182）

## 文件清单与使用场景

| 文件名 | 背景 | 用途 |
|---|---|---|
| logo-primary.jpg | 白底 | 竖版彩色组合（图标+字标），启动页/登录页/关于页 |
| logo-primary-transparent.png | 透明 | 同上，透明底，任意浅色页面直接叠放 |
| logo-icon.jpg | 白底 | 纯图标（无字），应用商店展示、头像 |
| logo-icon-transparent.png | 透明 | **App 桌面图标/加载角标/默认头像/favicon**，圆角外全透明 |
| logo-horizontal.jpg | 白底 | 横版组合（图标+黑字），浅色页面顶部导航 |
| logo-horizontal-transparent.png | 透明 | 同上，浅色导航栏直接叠放 |
| logo-horizontal-reverse.jpg | 黑底 | 横版反白（图标+白字），深色页/深色 Banner |
| logo-horizontal-reverse-transparent.png | 透明 | **深色导航栏专用**，透明底白字直接叠放 |
| logo-mono-black.jpg | 白底 | 纯黑单色版（图标+黑字），黑白印刷/传真/单色场景 |
| logo-reverse-white.jpg | 黑底 | 白色反白版（图标+白字），深色背景竖版展示 |

## 前端取用位置

- App 端（uni-app）：`xfjy-app/src/static/logo/`（全套 10 张）
- 管理端（Vue）：`xfjy-admin/public/logo/`（导航/图标/登录页常用 6 张，含 favicon.png）

## 使用规则

1. 浅色背景导航栏 → `logo-horizontal-transparent.png`（黑字）
2. 深色背景导航栏 → `logo-horizontal-reverse-transparent.png`（白字）
3. 启动页/登录页 → `logo-primary-transparent.png` 或白底 `logo-primary.jpg`
4. App 图标/头像/favicon → `logo-icon-transparent.png`
5. 不得拉伸变形、不得改变图标颜色、不得给图标加额外描边或阴影
6. 图标四周保留不少于图标高度 1/8 的安全留白
