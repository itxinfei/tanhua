package com.xfjy.service.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.xfjy.service.pojo.RecommendUser;
import com.xfjy.service.vo.PageInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;

@RestController
@RequestMapping("/api/recommend-user")
public class RecommendUserApiImpl implements RecommendUserApi {

    @Autowired
    private MongoTemplate mongoTemplate;

    /**
     * 今日佳人
     *
     * @param userId
     * @return
     */
    @Override
    @GetMapping("/queryWithMaxScore")
    public RecommendUser queryWithMaxScore(@RequestParam("userId") Long userId) {
        // 条件
        Criteria criteria = Criteria.where("toUserId").is(userId);
        // 按照得分倒序排序，获取第一条数据
        Query query = Query.query(criteria).with(Sort.by(Sort.Order.desc("score")))
                .limit(1);
        return this.mongoTemplate.findOne(query, RecommendUser.class);
    }

    /**
     * 推荐用户的列表查询
     *
     * @param userId
     * @param pageNum
     * @param pageSize
     * @return
     */
    @Override
    @GetMapping("/queryPageInfo")
    public PageInfo<RecommendUser> queryPageInfo(@RequestParam("userId") Long userId,
                                                 @RequestParam("pageNum") Integer pageNum,
                                                 @RequestParam("pageSize") Integer pageSize) {
        // 分页参数防御：pageNum 归一化到 >=1，pageSize 归一化到 >=1
        int p = (pageNum == null || pageNum < 1) ? 1 : pageNum;
        int size = (pageSize == null || pageSize < 1) ? 10 : pageSize;
        // 条件
        Criteria criteria = Criteria.where("toUserId").is(userId);
        Pageable pageable = PageRequest.of(p - 1, size, Sort.by(Sort.Order.desc("score")));
        Query query = Query.query(criteria).with(pageable);
        List<RecommendUser> recommendUsers = this.mongoTemplate.find(query, RecommendUser.class);

        // 补全 total 和 pages（原先硬编码 0，导致前端分页器无法计算总页数）
        long total = this.mongoTemplate.count(Query.query(criteria), RecommendUser.class);
        PageInfo<RecommendUser> pageInfo = new PageInfo<>();
        pageInfo.setTotal((int) total);
        pageInfo.setPageNum(p);
        pageInfo.setPageSize(size);
        pageInfo.setPages(size > 0 ? (int) ((total + size - 1) / size) : 0);
        pageInfo.setRecords(recommendUsers);
        return pageInfo;
    }

    @Override
    @GetMapping("/queryScore")
    public double queryScore(@RequestParam("userId") Long userId,
                             @RequestParam("toUserId") Long toUserId) {
        Query query = Query.query(Criteria
                .where("toUserId").is(toUserId)
                .and("userId").is(userId));
        RecommendUser recommendUser = this.mongoTemplate.findOne(query, RecommendUser.class);
        if (null == recommendUser) {
            return 0;
        }
        // getScore() 返回 Double，为 null 时自动拆箱会抛 NPE
        Double score = recommendUser.getScore();
        return score == null ? 0 : score;
    }
}