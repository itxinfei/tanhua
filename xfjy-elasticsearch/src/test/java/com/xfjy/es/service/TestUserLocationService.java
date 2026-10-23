package com.xfjy.es.service;

import com.xfjy.es.pojo.UserLocationES;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.geo.GeoPoint;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * UserLocationService 单元测试（纯 Mockito，不依赖 Elasticsearch 实例，可离线运行）
 */
@ExtendWith(MockitoExtension.class)
public class TestUserLocationService {

    @Mock
    private ElasticsearchOperations elasticsearchOperations;

    @InjectMocks
    private UserLocationService userLocationService;

    @Test
    void testUpdateUserLocation_whenNotExist_thenSaveAndReturnTrue() {
        when(elasticsearchOperations.get(anyString(), any())).thenReturn(null);
        when(elasticsearchOperations.save(any(UserLocationES.class))).thenReturn(new UserLocationES());

        boolean result = userLocationService.updateUserLocation(1L, 121.512253, 31.24094, "金茂大厦");
        assertTrue(result);
    }

    @Test
    void testUpdateUserLocation_whenExist_thenUpdateAndReturnTrue() {
        UserLocationES existing = new UserLocationES();
        existing.setUserId(1L);
        existing.setUpdated(1000L);
        when(elasticsearchOperations.get(anyString(), any())).thenReturn(existing);
        when(elasticsearchOperations.update(any(), any())).thenReturn(null);

        boolean result = userLocationService.updateUserLocation(1L, 121.5, 31.2, "东方明珠广播电视塔");
        assertTrue(result);
    }

    @Test
    void testQueryByUserId_found() {
        UserLocationES ul = new UserLocationES();
        ul.setUserId(1L);
        ul.setAddress("金茂大厦");
        when(elasticsearchOperations.get(eq("1"), eq(UserLocationES.class))).thenReturn(ul);

        UserLocationES result = userLocationService.queryByUserId(1L);
        assertNotNull(result);
        assertEquals("金茂大厦", result.getAddress());
    }

    @Test
    void testQueryByUserId_notFound() {
        when(elasticsearchOperations.get(anyString(), any())).thenReturn(null);

        assertNull(userLocationService.queryByUserId(99L));
    }

    @Test
    void testQueryUserFromLocation() {
        UserLocationES ul = new UserLocationES();
        ul.setUserId(10L);
        ul.setLocation(new GeoPoint(31.24, 121.51));

        SearchHit<UserLocationES> hit = mock(SearchHit.class);
        when(hit.getContent()).thenReturn(ul);

        SearchHits<UserLocationES> hits = mock(SearchHits.class);
        when(hits.getSearchHits()).thenReturn(List.of(hit));
        when(hits.getTotalHits()).thenReturn(1L);

        when(elasticsearchOperations.search(any(NativeQuery.class), eq(UserLocationES.class))).thenReturn(hits);

        Page<UserLocationES> page = userLocationService.queryUserFromLocation(121.512253, 31.24094, 1000d, 1, 100);
        assertNotNull(page);
        assertEquals(1L, page.getTotalElements());
        assertEquals(1, page.getContent().size());
        assertEquals(10L, page.getContent().get(0).getUserId());
    }
}
