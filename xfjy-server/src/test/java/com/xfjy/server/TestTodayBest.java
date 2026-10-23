package com.xfjy.server;

import com.xfjy.service.api.UserInfoApi;
import com.xfjy.service.enums.SexEnum;
import com.xfjy.service.pojo.RecommendUser;
import com.xfjy.service.pojo.UserInfo;
import com.xfjy.service.vo.PageInfo;
import com.xfjy.service.vo.UserInfoQueryParam;
import com.xfjy.server.pojo.User;
import com.xfjy.server.service.RecommendUserService;
import com.xfjy.server.service.TodayBestService;
import com.xfjy.server.utils.UserThreadLocal;
import com.xfjy.server.vo.PageResult;
import com.xfjy.server.vo.RecommendUserQueryParam;
import com.xfjy.server.vo.TodayBest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * TodayBestService 单元测试（纯 Mockito，不依赖任何中间件/微服务，可离线运行）
 */
@ExtendWith(MockitoExtension.class)
public class TestTodayBest {

    @Mock
    private RecommendUserService recommendUserService;

    @Mock
    private UserInfoApi userInfoApi;

    @InjectMocks
    private TodayBestService todayBestService;

    // 修改点：生产环境由 TokenInterceptor 写入当前登录用户，单元测试手动注入
    @BeforeEach
    void setUp() {
        User user = new User();
        user.setId(1L);
        UserThreadLocal.set(user);
    }

    @AfterEach
    void tearDown() {
        UserThreadLocal.remove();
    }

    @Test
    void testQueryTodayBest() {
        // 命中最高分推荐用户
        TodayBest recommend = new TodayBest();
        recommend.setId(100L);
        recommend.setFateValue(96L);
        when(recommendUserService.queryTodayBest(1L)).thenReturn(recommend);

        // 补全的用户信息
        UserInfo userInfo = new UserInfo();
        userInfo.setUserId(100L);
        userInfo.setNickName("小飞");
        userInfo.setLogo("http://img/100.png");
        userInfo.setSex(SexEnum.WOMAN);
        userInfo.setAge(22);
        userInfo.setTags("活泼,爱吃");
        when(userInfoApi.queryUserInfoById(100L)).thenReturn(userInfo);

        TodayBest todayBest = todayBestService.queryTodayBest();

        assertNotNull(todayBest);
        assertEquals(100L, todayBest.getId());
        assertEquals("小飞", todayBest.getNickname());
        assertEquals("woman", todayBest.getGender());
        assertEquals(22, todayBest.getAge());
        assertArrayEquals(new String[]{"活泼", "爱吃"}, todayBest.getTags());
        assertEquals(96L, todayBest.getFateValue());
    }

    @Test
    void testQueryRecommendUserList() {
        RecommendUser ru = new RecommendUser();
        ru.setUserId(200L);
        ru.setToUserId(1L);
        ru.setScore(88.0);

        PageInfo<RecommendUser> pageInfo = new PageInfo<>();
        pageInfo.setRecords(List.of(ru));
        when(recommendUserService.queryRecommendUserList(any(), any(), any())).thenReturn(pageInfo);

        UserInfo userInfo = new UserInfo();
        userInfo.setUserId(200L);
        userInfo.setNickName("小tan");
        userInfo.setLogo("http://img/200.png");
        userInfo.setSex(SexEnum.MAN);
        userInfo.setAge(25);
        userInfo.setTags("运动");
        when(userInfoApi.queryUserInfoList(any(UserInfoQueryParam.class))).thenReturn(List.of(userInfo));

        PageResult pageResult = todayBestService.queryRecommendUserList(new RecommendUserQueryParam());

        assertNotNull(pageResult);
        assertNotNull(pageResult.getItems());
        assertEquals(1, pageResult.getItems().size());
        TodayBest item = (TodayBest) pageResult.getItems().get(0);
        assertEquals(200L, item.getId());
        assertEquals("小tan", item.getNickname());
        assertEquals("man", item.getGender());
        assertEquals(88L, item.getFateValue());
    }
}
