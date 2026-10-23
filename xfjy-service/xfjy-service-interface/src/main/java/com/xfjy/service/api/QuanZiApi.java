package com.xfjy.service.api;

import com.xfjy.service.pojo.Comment;
import com.xfjy.service.pojo.Publish;
import com.xfjy.service.vo.PageInfo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.util.List;
import java.util.Map;

@HttpExchange("/api/quanzi")
public interface QuanZiApi {

    /**
     * 发布动态
     */
    @PostExchange("/savePublish")
    @PostMapping("/savePublish")
    String savePublish(@RequestBody Publish publish);

    /**
     * 查询好友动态
     */
    @GetExchange("/queryPublishList")
    @GetMapping("/queryPublishList")
    PageInfo<Publish> queryPublishList(@RequestParam("userId") Long userId,
                                       @RequestParam("page") Integer page,
                                       @RequestParam("pageSize") Integer pageSize);

    /**
     * 点赞
     */
    @PostExchange("/saveLikeComment")
    @PostMapping("/saveLikeComment")
    boolean saveLikeComment(@RequestParam("userId") Long userId,
                            @RequestParam("publishId") String publishId);

    /**
     * 取消点赞、喜欢等
     */
    @PostExchange("/removeComment")
    @PostMapping("/removeComment")
    boolean removeComment(@RequestParam("userId") Long userId,
                          @RequestParam("publishId") String publishId,
                          @RequestParam("commentType") Integer commentType);

    /**
     * 喜欢
     */
    @PostExchange("/saveLoveComment")
    @PostMapping("/saveLoveComment")
    boolean saveLoveComment(@RequestParam("userId") Long userId,
                            @RequestParam("publishId") String publishId);

    /**
     * 保存评论
     */
    @PostExchange("/saveComment")
    @PostMapping("/saveComment")
    boolean saveComment(@RequestParam("userId") Long userId,
                        @RequestParam("publishId") String publishId,
                        @RequestParam("type") Integer type,
                        @RequestParam("content") String content);

    /**
     * 查询评论数
     */
    @GetExchange("/queryCommentCount")
    @GetMapping("/queryCommentCount")
    Long queryCommentCount(@RequestParam("publishId") String publishId,
                           @RequestParam("type") Integer type);

    /**
     * 批量查询评论数（消除列表页 N+1 远程调用）
     * @param publishIds 动态id列表
     * @param type 评论类型（1-点赞 2-评论 3-喜欢）
     * @return publishId -> 评论数 映射，无数据的 publishId 不出现在结果中
     */
    @PostExchange("/queryCommentCountBatch")
    @PostMapping("/queryCommentCountBatch")
    Map<String, Long> queryCommentCountBatch(@RequestBody List<String> publishIds,
                                             @RequestParam("type") Integer type);

    /**
     * 根据id查询
     */
    @GetExchange("/queryPublishById")
    @GetMapping("/queryPublishById")
    Publish queryPublishById(@RequestParam("publishId") String publishId);

    /**
     * 记录动态浏览（评分事件 type=2，由圈子服务内部发送 xfjy-quanzi 消息）
     */
    @PostExchange("/recordPublishView")
    @PostMapping("/recordPublishView")
    boolean recordPublishView(@RequestParam("userId") Long userId,
                              @RequestParam("publishId") String publishId);

    /**
     * 查询评论
     */
    @GetExchange("/queryCommentList")
    @GetMapping("/queryCommentList")
    PageInfo<Comment> queryCommentList(@RequestParam("publishId") String publishId,
                                       @RequestParam("page") Integer page,
                                       @RequestParam("pageSize") Integer pageSize);

    /**
     * 根据pid批量查询数据
     */
    @PostExchange("/queryPublishByPids")
    @PostMapping("/queryPublishByPids")
    List<Publish> queryPublishByPids(@RequestBody List<Long> pids);

    /**
     * 查询用户的评论数据
     */
    @GetExchange("/queryCommentListByUser")
    @GetMapping("/queryCommentListByUser")
    PageInfo<Comment> queryCommentListByUser(@RequestParam("userId") Long userId,
                                             @RequestParam("type") Integer type,
                                             @RequestParam("page") Integer page,
                                             @RequestParam("pageSize") Integer pageSize);

    /**
     * 查询相册
     */
    @GetExchange("/queryAlbumList")
    @GetMapping("/queryAlbumList")
    PageInfo<Publish> queryAlbumList(@RequestParam("userId") Long userId,
                                     @RequestParam("page") Integer page,
                                     @RequestParam("pageSize") Integer pageSize);
}