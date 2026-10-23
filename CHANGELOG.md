# 变更记录（CHANGELOG）

格式：日期 | 模块 | 更新内容 | 开发者

2026-10-23 | xfjy-match-service | ES 地址配置化增强：@Value 增加本地兜底默认值（es.server.url 缺失时不致启动失败）；catch 块 e.printStackTrace() 替换为 SLF4J 日志（ESUserLocationApiImpl） | 开发者：itxinfei
2026-10-23 | xfjy-server (test) | TestTodayBest 改写为纯 Mockito 单元测试（@ExtendWith(MockitoExtension) + @Mock/@InjectMocks），mock RecommendUserService 与 UserInfoApi，不加载 Spring 上下文、不依赖任何中间件；离线可跑绿（Tests run: 2, Failures: 0） | 开发者：itxinfei
2026-10-23 | xfjy-elasticsearch (test) | TestUserLocationService 改写为纯 Mockito 单元测试，mock ElasticsearchOperations（get/save/update/search），覆盖新增/更新/按id查询/附近的人查询；不依赖 ES 实例，离线可跑绿（Tests run: 5, Failures: 0） | 开发者：itxinfei
2026-10-23 | 构建 | 任务53（单元测试+冒烟）达成：全量 mvn -o test BUILD SUCCESS；xfjy-spark 缺失依赖 parquet-jackson:1.17.0 已联网补齐至本地仓库 | 开发者：itxinfei
2026-10-23 | 全模块 | 统一微服务包名：com.xfjy.dubbo.server.* 整体重命名为 com.xfjy.service.*（api/pojo/vo/enums/config/service），消除与已弃用 Dubbo 矛盾的命名债务；涉及 xfjy-service-interface、xfjy-server、xfjy-recommend 及 circle/match/message/user/video 五拆分服务，git mv 目录 + 前缀替换 import，mvn -o clean compile/test 均 BUILD SUCCESS | 开发者：itxinfei
2026-10-23 | docs | README 同步实际模块结构：目录结构/职责表/架构总览补 xfjy-match-service(18082) 与 xfjy-message-service(18086)，删除已退役的 xfjy-service-core，xfjy-service 标注为仅含 interface 契约；新增网关路由说明（仅边缘路由 sso+server，拆分服务由 server 内部直连） | 开发者：itxinfei
2026-10-23 | scripts | 新增端到端冒烟测试脚本 scripts/e2e-test.sh：经网关(8080)覆盖完整链路（端口存活检查 + sso 登录取 token + 今日佳人/推荐/卡片/用户/动态/环信/消息联系人/公告），token 走 Authorization 头；支持 TOKEN/PHONE/CODE/GATEWAY 环境变量，纯 curl 无额外依赖；本机无中间件环境实测可运行并正确汇总 FAIL（exit 1） | 开发者：itxinfei
