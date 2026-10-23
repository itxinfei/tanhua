package com.xfjy.service.api;

import com.xfjy.service.pojo.RecommendUser;
import com.xfjy.service.vo.PageInfo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange("/api/recommend-user")
public interface RecommendUserApi {

    /**
     * 查询一位得分最高的推荐用户
     */
    @GetExchange("/queryWithMaxScore")
    @GetMapping("/queryWithMaxScore")
    RecommendUser queryWithMaxScore(@RequestParam("userId") Long userId);

    /**
     * 按照得分倒序
     */
    @GetExchange("/queryPageInfo")
    @GetMapping("/queryPageInfo")
    PageInfo<RecommendUser> queryPageInfo(@RequestParam("userId") Long userId,
                                          @RequestParam("pageNum") Integer pageNum,
                                          @RequestParam("pageSize") Integer pageSize);

    /**
     * 查询推荐好友的缘分值
     */
    @GetExchange("/queryScore")
    @GetMapping("/queryScore")
    double queryScore(@RequestParam("userId") Long userId,
                      @RequestParam("toUserId") Long toUserId);
}