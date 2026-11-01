package com.xfjy.server.controller;

import com.xfjy.server.service.TodayBestService;
import com.xfjy.server.vo.NearUserVo;
import com.xfjy.server.vo.PageResult;
import com.xfjy.server.vo.RecommendUserQueryParam;
import com.xfjy.server.vo.TodayBest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("xfjy")
public class TodayBestController {

    @Autowired
    private TodayBestService todayBestService;

    @GetMapping("todayBest")
    public TodayBest queryTodayBest() {
        return this.todayBestService.queryTodayBest();
    }

    @GetMapping("recommendation")
    public PageResult queryRecommendUserList(RecommendUserQueryParam queryParam) {
        return this.todayBestService.queryRecommendUserList(queryParam);
    }

    /**
     * 查询今日佳人详情
     *
     * @param userId
     * @return
     */
    @GetMapping("{id}/personalInfo")
    public TodayBest queryTodayBest(@PathVariable("id") Long userId) {
        return this.todayBestService.queryTodayBest(userId);
    }

    /**
     * 查询陌生人问题
     *
     * @param userId
     * @return
     */
    @GetMapping("strangerQuestions")
    public String queryQuestion(@RequestParam("userId") Long userId) {
        return this.todayBestService.queryQuestion(userId);
    }

    /**
     * 回复陌生人问题
     *
     * @return
     */
    @PostMapping("strangerQuestions")
    public Boolean replyQuestion(@RequestBody Map<String, Object> param) {
        Long userId = Long.valueOf(param.get("userId").toString());
        String reply = param.get("reply").toString();
        return this.todayBestService.replyQuestion(userId, reply);
    }

    /**
     * 搜附近
     *
     * @param gender
     * @param distance
     * @return
     */
    @GetMapping("search")
    public List<NearUserVo> queryNearUser(@RequestParam(value = "gender", required = false) String gender,
                                          @RequestParam(value = "distance", defaultValue = "2000") String distance) {
        return this.todayBestService.queryNearUser(gender, distance);
    }


    /**
     * 探花
     *
     * @return
     */
    @GetMapping("cards")
    public List<TodayBest> queryCardsList() {
        return this.todayBestService.queryCardsList();
    }

    /**
     * 喜欢（探花右滑/超级喜欢）
     *
     * @param likeUserId 被喜欢的用户ID
     * @param action     动作类型：like|superlike（默认 like）；superlike 会校验并扣减 super_like 权益
     * @return true=互相喜欢（匹配成功）；false=单方面喜欢
     */
    @GetMapping("{id}/love")
    public Boolean likeUser(@PathVariable("id") Long likeUserId,
                            @RequestParam(value = "action", required = false, defaultValue = "like") String action) {
        return this.todayBestService.likeUser(likeUserId, action);
    }

    /**
     * 不喜欢
     *
     * @param likeUserId
     * @return
     */
    @GetMapping("{id}/unlove")
    public void disLikeUser(@PathVariable("id") Long likeUserId) {
        this.todayBestService.disLikeUser(likeUserId);
    }


}
