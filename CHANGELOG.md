# 变更记录（CHANGELOG）

格式：日期 | 模块 | 更新内容 | 开发者

2026-10-23 | xfjy-match-service | ES 地址配置化增强：@Value 增加本地兜底默认值（es.server.url 缺失时不致启动失败）；catch 块 e.printStackTrace() 替换为 SLF4J 日志（ESUserLocationApiImpl） | 开发者：itxinfei
2026-10-23 | xfjy-server (test) | TestTodayBest 改写为纯 Mockito 单元测试（@ExtendWith(MockitoExtension) + @Mock/@InjectMocks），mock RecommendUserService 与 UserInfoApi，不加载 Spring 上下文、不依赖任何中间件；离线可跑绿（Tests run: 2, Failures: 0） | 开发者：itxinfei
2026-10-23 | xfjy-elasticsearch (test) | TestUserLocationService 改写为纯 Mockito 单元测试，mock ElasticsearchOperations（get/save/update/search），覆盖新增/更新/按id查询/附近的人查询；不依赖 ES 实例，离线可跑绿（Tests run: 5, Failures: 0） | 开发者：itxinfei
2026-10-23 | 构建 | 任务53（单元测试+冒烟）达成：全量 mvn -o test BUILD SUCCESS；xfjy-spark 缺失依赖 parquet-jackson:1.17.0 已联网补齐至本地仓库 | 开发者：itxinfei
2026-10-23 | 全模块 | 统一微服务包名：com.xfjy.dubbo.server.* 整体重命名为 com.xfjy.service.*（api/pojo/vo/enums/config/service），消除与已弃用 Dubbo 矛盾的命名债务；涉及 xfjy-service-interface、xfjy-server、xfjy-recommend 及 circle/match/message/user/video 五拆分服务，git mv 目录 + 前缀替换 import，mvn -o clean compile/test 均 BUILD SUCCESS | 开发者：itxinfei
2026-10-23 | docs | README 同步实际模块结构：目录结构/职责表/架构总览补 xfjy-match-service(18082) 与 xfjy-message-service(18086)，删除已退役的 xfjy-service-core，xfjy-service 标注为仅含 interface 契约；新增网关路由说明（仅边缘路由 sso+server，拆分服务由 server 内部直连） | 开发者：itxinfei
2026-10-23 | scripts | 新增端到端冒烟测试脚本 scripts/e2e-test.sh：经网关(8080)覆盖完整链路（端口存活 + sso 登录取 token + 今日佳人/推荐/卡片/用户/动态/环信/消息联系人/公告），token 走 Authorization 头；支持 TOKEN/PHONE/CODE/GATEWAY 环境变量，纯 curl 无额外依赖；本机无中间件环境实测可运行并正确汇总 FAIL（exit 1） | 开发者：itxinfei
2026-10-23 | docs | 新增功能就绪矩阵 docs/功能就绪矩阵.md：逐功能标注 代码实现/单测/端到端验证/依赖中间件/已知缺口；汇总非 IM 待办（圈子事务回滚、动态距离与评论数硬编码、视频签名/关注硬编码、推荐缓存、年龄城市筛选未启用）与环境阻塞（Mongo/ES/RocketMQ 未装、Spark 未实跑）；明确技术栈与功能范围已确定，落地验证部分确定，实时聊天待用户申请环信凭证 | 开发者：itxinfei
2026-10-23 | 全模块 | 功能就绪矩阵 5 项非 IM 待办全部清零：①QuanziApiImpl 发布动态补补偿回滚（相册/时间线写入失败逐级回滚，兼容单机 MongoDB）；②MovementsService 动态距离改 Haversine 球面真实距离+评论数改 queryCommentCount 真实值（用户实现）；③TodayBestService 默认推荐列表 double-check lazy 缓存；④VideoService 签名取用户 tags+关注状态查 Redis VIDEO_FOLLOW_USER_ 键；⑤UserInfoQueryParam 扩展 age/city 字段+UserInfoApiImpl 落地 eq 条件+TodayBestService 传参启用筛选；mvn -o compile BUILD SUCCESS | 开发者：itxinfei
2026-10-23 | perf | 消除动态/视频列表 N+1 远程调用：QuanZiApi 契约新增 queryCommentCountBatch（POST /api/quanzi/queryCommentCountBatch，入参 publishIds+type，返回 Map<publishId,count>）；QuanziApiImpl 用 MongoDB Aggregation（$match+$group）单次聚合实现；MovementsService.fillValueToMovements 与 VideoService.queryVideoList 改循环外批量查询+循环内 map 查找，分页 10 条由 10 次 RPC 降为 1 次；容错降级——批量查询失败时评论数显示 0 不阻断列表 | 开发者：itxinfei
