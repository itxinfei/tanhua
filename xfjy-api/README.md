# xfjy-api — 内部服务接口契约

微服务间 HTTP 调用的契约模块：定义 `@HttpExchange` 接口与共享 pojo/vo。消费方（xfjy-server）引入本模块即可声明式调用各域服务，无需手写 HTTP 客户端。

## 接口清单

| 接口 | 路径前缀 | 提供方 | 职责 |
|------|----------|--------|------|
| `UsersApi` | `/api/users` | xfjy-user-service | 好友/喜欢/访客关系 |
| `UserInfoApi` | `/api/userInfo` | xfjy-user-service | 用户资料读写 |
| `UserLikeApi` | `/api/user-like` | xfjy-user-service | 喜欢/匹配状态 |
| `VisitorsApi` | `/api/visitors` | xfjy-user-service | 访客记录 |
| `QuanZiApi` | `/api/quanzi` | xfjy-circle-service | 动态/评论/时间线 |
| `VideoApi` | `/api/video` | xfjy-video-service | 小视频/关注作者 |
| `HuanXinApi` | `/api/huanxin` | xfjy-message-service | 环信用户注册/消息下发 |
| `RecommendUserApi` | `/api/recommend-user` | xfjy-match-service | 推荐候选/地理位置 |
| `UserLocationApi` | `/api/user-location` | xfjy-match-service | 地理位置上报 |
| `ESUserLocationApi` | `/api/es-user-location` | xfjy-elasticsearch | ES Geo 附近的人 |
| `PayApi` | `/api/pay` | xfjy-pay-service | 支付下单/VIP 权益校验 |

## 调用方式

消费方声明接口类型的 Bean，由 Spring 自动生成代理（`RestClient` 底层），实例地址经 Nacos `DiscoveryClient` 动态解析或直配 URL：

```java
@Autowired
private QuanZiApi quanZiApi;

QuanZiVo vo = quanZiApi.queryQuanZi(userId, page, pageSize);
```

## 构建注意

本模块被多服务依赖，改动后须连带构建：

```bash
mvn -pl xfjy-api -am install
```