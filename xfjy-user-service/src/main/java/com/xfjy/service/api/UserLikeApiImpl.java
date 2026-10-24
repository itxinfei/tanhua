package com.xfjy.service.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.mongodb.client.result.DeleteResult;
import com.xfjy.service.pojo.UserLike;
import com.xfjy.service.vo.PageInfo;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/user-like")
public class UserLikeApiImpl implements UserLikeApi {

    @Autowired
    private MongoTemplate mongoTemplate;

    @Override
    @PostMapping("/saveUserLike")
    public String saveUserLike(@RequestParam("userId") Long userId,
                               @RequestParam("likeUserId") Long likeUserId) {
        // 判断喜欢关系是否存在
        Query query = Query.query(Criteria.where("userId").is(userId)
                .and("likeUserId").is(likeUserId));

        if (this.mongoTemplate.count(query, UserLike.class) > 0) {
            return null;
        }

        UserLike userLike = new UserLike();
        userLike.setId(ObjectId.get());
        userLike.setLikeUserId(likeUserId);
        userLike.setUserId(userId);
        userLike.setCreated(System.currentTimeMillis());

        this.mongoTemplate.save(userLike);
        return userLike.getId().toHexString();
    }

    /**
     * 是否互相喜欢
     *
     * @param userId
     * @param likeUserId
     * @return
     */
    @Override
    @GetMapping("/isMutualLike")
    public Boolean isMutualLike(@RequestParam("userId") Long userId,
                                @RequestParam("likeUserId") Long likeUserId) {
        Criteria criteria1 = Criteria.where("userId").is(userId).and("likeUserId").is(likeUserId);
        Criteria criteria2 = Criteria.where("userId").is(likeUserId).and("likeUserId").is(userId);

        Criteria criteria = new Criteria().orOperator(criteria1, criteria2);
        return this.mongoTemplate.count(Query.query(criteria), UserLike.class) == 2;
    }

    @Override
    @PostMapping("/deleteUserLike")
    public Boolean deleteUserLike(@RequestParam("userId") Long userId,
                                  @RequestParam("likeUserId") Long likeUserId) {
        Query query = Query.query(Criteria.where("userId").is(userId)
                .and("likeUserId").is(likeUserId));
        DeleteResult deleteResult = this.mongoTemplate.remove(query, UserLike.class);
        return deleteResult.getDeletedCount() == 1;
    }

    @Override
    @GetMapping("/queryEachLikeCount")
    public Long queryEachLikeCount(@RequestParam("userId") Long userId) {
        // 思路：

        // 查询我的喜欢列表
        Query query = Query.query(Criteria.where("userId").is(userId));
        List<UserLike> userLikes = this.mongoTemplate.find(query, UserLike.class);

        // 收集到我的喜欢列表中的用户id（对方）
        List<Long> likeUserIds = new ArrayList<>();
        for (UserLike userLike : userLikes) {
            likeUserIds.add(userLike.getLikeUserId());
        }

        // 在我的喜欢列表范围内，查询喜欢我的人有哪些
        Query query2 = Query.query(Criteria.where("userId").in(likeUserIds).and("likeUserId").is(userId));

        return this.mongoTemplate.count(query2, UserLike.class);
    }

    @Override
    @GetMapping("/queryLikeCount")
    public Long queryLikeCount(@RequestParam("userId") Long userId) {
        return this.mongoTemplate.count(Query.query(Criteria.where("userId").is(userId)), UserLike.class);
    }

    @Override
    @GetMapping("/queryFanCount")
    public Long queryFanCount(@RequestParam("userId") Long userId) {
        return this.mongoTemplate.count(Query.query(Criteria.where("likeUserId").is(userId)), UserLike.class);
    }

    @Override
    @GetMapping("/queryEachLikeList")
    public PageInfo<UserLike> queryEachLikeList(@RequestParam("userId") Long userId,
                                                @RequestParam("page") Integer page,
                                                @RequestParam("pageSize") Integer pageSize) {

        // 查询我的喜欢列表
        Query query = Query.query(Criteria.where("userId").is(userId));
        List<UserLike> userLikes = this.mongoTemplate.find(query, UserLike.class);

        // 收集到我的喜欢列表中的用户id（对方）
        List<Long> likeUserIds = new ArrayList<>();
        for (UserLike userLike : userLikes) {
            likeUserIds.add(userLike.getLikeUserId());
        }

        // 在我的喜欢列表范围内，查询喜欢我的人有哪些
        Query query2 = Query.query(Criteria.where("userId").in(likeUserIds).and("likeUserId").is(userId));
        return this.queryList(query2, page, pageSize);
    }

    @Override
    @GetMapping("/queryLikeList")
    public PageInfo<UserLike> queryLikeList(@RequestParam("userId") Long userId,
                                            @RequestParam("page") Integer page,
                                            @RequestParam("pageSize") Integer pageSize) {
        return this.queryList(Query.query(Criteria.where("userId").is(userId)), page, pageSize);
    }

    @Override
    @GetMapping("/queryFanList")
    public PageInfo<UserLike> queryFanList(@RequestParam("userId") Long userId,
                                           @RequestParam("page") Integer page,
                                           @RequestParam("pageSize") Integer pageSize) {
        return this.queryList(Query.query(Criteria.where("likeUserId").is(userId)), page, pageSize);
    }

    private PageInfo<UserLike> queryList(Query query, Integer page, Integer pageSize){
        PageRequest pageRequest = PageRequest.of(page - 1, pageSize, Sort.by(Sort.Order.desc("created")));
        query.with(pageRequest); //分页
        List<UserLike> userLikeList = this.mongoTemplate.find(query, UserLike.class);

        PageInfo<UserLike> pageInfo = new PageInfo<>();
        pageInfo.setPageNum(page);
        pageInfo.setPageSize(pageSize);
        pageInfo.setTotal(0); //暂时不提供
        pageInfo.setRecords(userLikeList);

        return pageInfo;
    }
}