package com.xfjy.dubbo.server.api;

import com.xfjy.dubbo.server.pojo.UserLike;
import com.xfjy.dubbo.server.vo.PageInfo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange("/api/user-like")
public interface UserLikeApi {

    /**
     * 保存喜欢
     */
    @PostExchange("/saveUserLike")
    @PostMapping("/saveUserLike")
    String saveUserLike(@RequestParam("userId") Long userId,
                        @RequestParam("likeUserId") Long likeUserId);

    /**
     * 相互喜欢
     */
    @GetExchange("/isMutualLike")
    @GetMapping("/isMutualLike")
    Boolean isMutualLike(@RequestParam("userId") Long userId,
                         @RequestParam("likeUserId") Long likeUserId);

    /**
     * 删除用户喜欢
     */
    @PostExchange("/deleteUserLike")
    @PostMapping("/deleteUserLike")
    Boolean deleteUserLike(@RequestParam("userId") Long userId,
                           @RequestParam("likeUserId") Long likeUserId);

    /**
     * 相互喜欢的数量
     */
    @GetExchange("/queryEachLikeCount")
    @GetMapping("/queryEachLikeCount")
    Long queryEachLikeCount(@RequestParam("userId") Long userId);

    /**
     * 喜欢数
     */
    @GetExchange("/queryLikeCount")
    @GetMapping("/queryLikeCount")
    Long queryLikeCount(@RequestParam("userId") Long userId);

    /**
     * 粉丝数
     */
    @GetExchange("/queryFanCount")
    @GetMapping("/queryFanCount")
    Long queryFanCount(@RequestParam("userId") Long userId);

    /**
     * 查询相互喜欢列表
     */
    @GetExchange("/queryEachLikeList")
    @GetMapping("/queryEachLikeList")
    PageInfo<UserLike> queryEachLikeList(@RequestParam("userId") Long userId,
                                         @RequestParam("page") Integer page,
                                         @RequestParam("pageSize") Integer pageSize);

    /**
     * 查询我喜欢的列表
     */
    @GetExchange("/queryLikeList")
    @GetMapping("/queryLikeList")
    PageInfo<UserLike> queryLikeList(@RequestParam("userId") Long userId,
                                     @RequestParam("page") Integer page,
                                     @RequestParam("pageSize") Integer pageSize);

    /**
     * 查询粉丝列表
     */
    @GetExchange("/queryFanList")
    @GetMapping("/queryFanList")
    PageInfo<UserLike> queryFanList(@RequestParam("userId") Long userId,
                                    @RequestParam("page") Integer page,
                                    @RequestParam("pageSize") Integer pageSize);
}