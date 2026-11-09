package com.xfjy.es.service;

import co.elastic.clients.elasticsearch._types.DistanceUnit;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.xfjy.es.pojo.UserLocationES;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import jakarta.annotation.PostConstruct;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.geo.GeoPoint;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserLocationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserLocationService.class);

    @Autowired
    private ElasticsearchOperations elasticsearchOperations;

    /**
     * 启动时确保索引存在：spring-data-elasticsearch 6.x 的 save/get 不会自动建索引，
     * 索引缺失时 get() 会抛 NoSuchIndexException 导致写入静默失败。
     * createWithMapping() 依据 @Field/@GeoPointField 注解生成映射（location 为 geo_point）。
     */
    @PostConstruct
    public void initIndex() {
        try {
            IndexOperations indexOps = elasticsearchOperations.indexOps(UserLocationES.class);
            if (!indexOps.exists()) {
                indexOps.createWithMapping();
                LOGGER.info("ES索引 xfjy_user_location 创建成功");
            }
        } catch (Exception e) {
            // ES 不可用时不阻断服务启动，首次写入时由调用方感知异常
            LOGGER.error("ES索引 xfjy_user_location 初始化失败", e);
        }
    }

    /**
     * 更新用户地理位置（不存在则新增，存在则覆盖）
     *
     * <p>这里刻意用整文档覆盖（save 走 _index API）而不是「先 get 再 update」：
     * _update API 内部是读-改-写，并发上报同一用户时 ES 会用内部版本做乐观锁，
     * 后到的请求会抛 version_conflict_engine_exception，导致接口 500。
     * _index 不带版本前置条件，也不存在读-改-写窗口，并发请求按到达顺序覆盖，
     * 最终留下最新一次上报的位置，正好是位置上报想要的语义。
     * get 只用于继承 created / 旧 address，读操作不会产生版本冲突。</p>
     */
    public boolean updateUserLocation(Long userId, Double longitude, Double latitude, String address) {
        try {
            long now = System.currentTimeMillis();
            UserLocationES existing = elasticsearchOperations.get(userId.toString(), UserLocationES.class);

            UserLocationES ul = new UserLocationES();
            ul.setUserId(userId);
            ul.setLocation(new GeoPoint(latitude, longitude));
            // address 为 null 时沿用旧值，避免清空 ES 中已存储的地址
            ul.setAddress(address != null ? address : (existing != null ? existing.getAddress() : null));
            ul.setCreated(existing != null && existing.getCreated() != null ? existing.getCreated() : now);
            ul.setLastUpdated(existing != null && existing.getUpdated() != null ? existing.getUpdated() : now);
            ul.setUpdated(now);

            elasticsearchOperations.save(ul);
            return true;
        } catch (Exception e) {
            LOGGER.error("ES地理位置服务异常", e);
        }
        return false;
    }

    /**
     * 根据用户id查询地理位置
     */
    public UserLocationES queryByUserId(Long userId) {
        return elasticsearchOperations.get(userId.toString(), UserLocationES.class);
    }

    /**
     * 查询附近的人
     *
     * @param longitude 经度
     * @param latitude  纬度
     * @param distance  距离（单位：米）
     * @param page      页数
     * @param pageSize  页面大小
     */
    public Page<UserLocationES> queryUserFromLocation(Double longitude, Double latitude, Double distance,
                                                      Integer page, Integer pageSize) {
        String distanceKm = String.format("%.1fkm", distance / 1000);

        // geo_distance 查询：以当前坐标为中心，搜索指定范围内的用户
        Query geoQuery = Query.of(q -> q
                .geoDistance(gd -> gd
                        .field("location")
                        .distance(distanceKm)
                        .location(l -> l
                                .latlon(ll -> ll.lat(latitude).lon(longitude))
                        )
                )
        );

        // 按距离升序排序
        SortOptions sortOptions = SortOptions.of(s -> s
                .geoDistance(gd -> gd
                        .field("location")
                        .location(l -> l
                                .latlon(ll -> ll.lat(latitude).lon(longitude))
                        )
                        .order(SortOrder.Asc)
                        .unit(DistanceUnit.Kilometers)
                )
        );

        NativeQuery nativeQuery = NativeQuery.builder()
                .withQuery(geoQuery)
                .withSort(sortOptions)
                .withPageable(PageRequest.of(page - 1, pageSize))
                .build();

        SearchHits<UserLocationES> searchHits = elasticsearchOperations.search(nativeQuery, UserLocationES.class);
        List<UserLocationES> content = searchHits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .toList();

        return new PageImpl<>(content, PageRequest.of(page - 1, pageSize), searchHits.getTotalHits());
    }
}