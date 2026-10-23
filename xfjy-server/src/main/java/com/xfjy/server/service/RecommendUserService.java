package com.xfjy.server.service;

import org.springframework.beans.factory.annotation.Autowired;
import com.xfjy.service.api.RecommendUserApi;
import com.xfjy.service.pojo.RecommendUser;
import com.xfjy.service.vo.PageInfo;
import com.xfjy.server.vo.TodayBest;
import org.springframework.stereotype.Service;

@Service
public class RecommendUserService {

    @Autowired
    private RecommendUserApi recommendUserApi;

    public TodayBest queryTodayBest(Long userId) {
        RecommendUser recommendUser = this.recommendUserApi.queryWithMaxScore(userId);
        if(null == recommendUser){
            return null;
        }

        TodayBest todayBest = new TodayBest();
        // 如果得分大于8.2时，需要显示的得分加8
        double score = Math.floor(recommendUser.getScore());
        todayBest.setFateValue(Double.valueOf(score).longValue()); //缘分值

        todayBest.setId(recommendUser.getUserId()); //用户id

        return todayBest;
    }

    public PageInfo<RecommendUser> queryRecommendUserList(Long id, Integer page, Integer pagesize) {
        return this.recommendUserApi.queryPageInfo(id,page,pagesize);
    }

    /**
     * 查询推荐好友的缘分值
     *
     * @param userId
     * @param toUserId
     * @return
     */
    double queryScore(Long userId, Long toUserId) {
        return this.recommendUserApi.queryScore(userId, toUserId);
    }
}
