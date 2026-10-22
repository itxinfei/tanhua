package com.xfjy.server;

import com.xfjy.server.service.TodayBestService;
import com.xfjy.server.vo.PageResult;
import com.xfjy.server.vo.RecommendUserQueryParam;
import com.xfjy.server.vo.TodayBest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class TestTodayBest {

    @Autowired
    private TodayBestService todayBestService;

    @Test
    public void testQueryTodayBest(){
        TodayBest todayBest = this.todayBestService.queryTodayBest();
        System.out.println(todayBest);
    }

    @Test
    public void testQueryTodayBestList(){
        PageResult pageResult = this.todayBestService.queryRecommendUserList(new RecommendUserQueryParam());
        System.out.println(pageResult);
    }
}