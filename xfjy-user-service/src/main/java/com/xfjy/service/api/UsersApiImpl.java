package com.xfjy.service.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.xfjy.service.pojo.Users;
import com.xfjy.service.vo.PageInfo;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UsersApiImpl implements UsersApi {

    @Autowired
    private MongoTemplate mongoTemplate;

    @Override
    @PostMapping("/saveUsers")
    public String saveUsers(@RequestBody Users users) {

        //校验
        if (users.getUserId() == null || users.getFriendId() == null) {
            return null;
        }

        Query query = Query.query(Criteria.where("userId")
                .is(users.getUserId())
                .and("friendId")
                .is(users.getFriendId()));
        Users oldUsers = this.mongoTemplate.findOne(query, Users.class);
        if (null != oldUsers) {
            //该好友的关系已经存在
            return null;
        }

        users.setId(ObjectId.get());
        users.setDate(System.currentTimeMillis());

        //将数据写入到MongoDB
        this.mongoTemplate.save(users);

        return users.getId().toHexString();
    }

    @Override
    @GetMapping("/queryAllUsersList")
    public List<Users> queryAllUsersList(@RequestParam("userId") Long userId) {
        Query query = Query.query(Criteria.where("userId").is(userId));
        return this.mongoTemplate.find(query, Users.class);
    }

    @Override
    @GetMapping("/queryUsersList")
    public PageInfo<Users> queryUsersList(@RequestParam("userId") Long userId,
                                          @RequestParam("page") Integer page,
                                          @RequestParam("pageSize") Integer pageSize) {
        PageRequest pageRequest = PageRequest.of(page - 1, pageSize, Sort.by(Sort.Order.desc("created")));
        Query query = Query.query(Criteria.where("userId").is(userId)).with(pageRequest);

        List<Users> usersList = this.mongoTemplate.find(query, Users.class);

        PageInfo<Users> pageInfo = new PageInfo<>();
        pageInfo.setPageNum(page);
        pageInfo.setPageSize(pageSize);
        pageInfo.setRecords(usersList);
        pageInfo.setTotal(0); //不提供总数
        return pageInfo;
    }
}