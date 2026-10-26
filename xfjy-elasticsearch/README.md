# xfjy-elasticsearch — 地理位置搜索服务

基于 Elasticsearch Geo 的"附近的人"搜索服务（端口 18088）。用户上报经纬度写入 ES 索引，检索时按地理距离排序返回附近用户。

## 核心能力

| 能力 | 说明 |
|------|------|
| 位置上报 | 用户经纬度写入 ES `user_location` 索引（geo_point 类型） |
| 附近的人 | `geo_distance` 查询 + 距离排序，支持分页与距离过滤半径 |
| 契约暴露 | `ESUserLocationApi`（`/api/es-user-location`）供 xfjy-server 调用 |

## 数据模型

ES 索引 `user_location` 核心字段：

| 字段 | 类型 | 说明 |
|------|------|------|
| `userId` | long | 用户 ID |
| `location` | geo_point | 经纬度 |
| `addrStr` | keyword | 地址描述 |

## 启动

```bash
# 需先启动 Elasticsearch（8.x）
mvn -pl xfjy-elasticsearch -am spring-boot:run
```

ES 连接配置走环境变量（见 `.env.example`），索引不存在时自动创建。