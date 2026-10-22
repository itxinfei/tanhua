package com.xfjy.es.service;

import co.elastic.clients.elasticsearch._types.DistanceUnit;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.xfjy.es.pojo.UserLocationES;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.geo.GeoPoint;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.UpdateQuery;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class UserLocationService {

    @Autowired
    private ElasticsearchOperations elasticsearchOperations;

    /**
     * 更新用户地理位置（不存在则新增，存在则更新）
     */
    public boolean updateUserLocation(Long userId, Double longitude, Double latitude, String address) {
        try {
            UserLocationES ul = elasticsearchOperations.get(userId.toString(), UserLocationES.class);
            if (null == ul) {
                // 新增位置数据
                UserLocationES userLocationES = new UserLocationES();
                userLocationES.setAddress(address);
                userLocationES.setCreated(System.currentTimeMillis());
                userLocationES.setLastUpdated(userLocationES.getCreated());
                userLocationES.setUpdated(userLocationES.getCreated());
                userLocationES.setUserId(userId);
                userLocationES.setLocation(new GeoPoint(latitude, longitude));
                elasticsearchOperations.save(userLocationES);
            } else {
                // 更新操作
                Map<String, Object> map = new HashMap<>();
                map.put("lastUpdated", ul.getUpdated());
                map.put("updated", System.currentTimeMillis());
                map.put("address", address);
                Map<String, Object> geoMap = new HashMap<>();
                geoMap.put("lat", latitude);
                geoMap.put("lon", longitude);
                map.put("location", geoMap);

                UpdateQuery updateQuery = UpdateQuery.builder(userId.toString())
                        .withDocument(Document.from(map))
                        .build();
                elasticsearchOperations.update(updateQuery, IndexCoordinates.of("xfjy_user_location"));
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
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