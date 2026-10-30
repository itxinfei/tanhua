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
     * 保存回复评论（挂到父评论下，isParent=false）
     *
     * @param userId   回复人用户ID
     * @param publishId 动态ID
     * @param parentId 父评论ID（回复"回复"时传根评论ID，由调用方归一）
     * @param content  回复内容
     */
    @PostExchange("/saveReplyComment")
    @PostMapping("/saveReplyComment")
    boolean saveReplyComment(@RequestParam("userId") Long userId,
                             @RequestParam("publishId") String publishId,
                             @RequestParam("parentId") String parentId,
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
     * 根据id查询评论（评论点赞通知评论作者用）
     */
    @GetExchange("/queryCommentById")
    @GetMapping("/queryCommentById")
    Comment queryCommentById(@RequestParam("commentId") String commentId);

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

    /**
     * 删除动态（内容审核驳回时联动下架）
     *
     * <p>删除范围：动态主表 + 发布者相册 + 好友时间线中的该动态记录</p>
     *
     * @param publishId 动态ID
     * @return 是否删除成功（动态不存在返回 false）
     */
    @PostExchange("/removePublish")
    @PostMapping("/removePublish")
    boolean removePublish(@RequestParam("publishId") String publishId);

    /**
     * 管理后台全量分页查询动态（按发布时间倒序，支持关键词/用户ID/日期范围筛选）
     *
     * @param page     页码（从1开始，默认1）
     * @param pageSize 每页条数（默认10）
     * @param keyword  文字内容关键词（模糊匹配，可选）
     * @param userId   发布者用户ID（可选）
     * @param startTime 开始时间戳（可选，毫秒）
     * @param endTime   结束时间戳（可选，毫秒）
     * @return 分页结果（含总条数）
     */
    @GetExchange("/queryAllPublishList")
    @GetMapping("/queryAllPublishList")
    PageInfo<Publish> queryAllPublishList(@RequestParam(value = "page", required = false) Integer page,
                                           @RequestParam(value = "pageSize", required = false) Integer pageSize,
                                           @RequestParam(value = "keyword", required = false) String keyword,
                                           @RequestParam(value = "userId", required = false) Long userId,
                                           @RequestParam(value = "startTime", required = false) Long startTime,
                                           @RequestParam(value = "endTime", required = false) Long endTime);

    /**
     * 管理后台全量分页查询文字评论（commentType=2，按发表时间倒序，支持关键词/用户ID/日期范围筛选）
     *
     * @param page     页码（从1开始，默认1）
     * @param pageSize 每页条数（默认10）
     * @param keyword  评论内容关键词（模糊匹配，可选）
     * @param userId   评论人用户ID（可选）
     * @param startTime 开始时间戳（可选，毫秒）
     * @param endTime   结束时间戳（可选，毫秒）
     * @return 分页结果（含总条数）
     */
    @GetExchange("/queryAllCommentList")
    @GetMapping("/queryAllCommentList")
    PageInfo<Comment> queryAllCommentList(@RequestParam(value = "page", required = false) Integer page,
                                           @RequestParam(value = "pageSize", required = false) Integer pageSize,
                                           @RequestParam(value = "keyword", required = false) String keyword,
                                           @RequestParam(value = "userId", required = false) Long userId,
                                           @RequestParam(value = "startTime", required = false) Long startTime,
                                           @RequestParam(value = "endTime", required = false) Long endTime);

    /**
     * 管理后台根据评论ID删除评论
     *
     * @param commentId 评论ID（MongoDB ObjectId 字符串）
     * @return 是否删除成功（评论不存在返回 false）
     */
    @PostExchange("/removeCommentById")
    @PostMapping("/removeCommentById")
    boolean removeCommentById(@RequestParam("commentId") String commentId);

    /**
     * 统计用户发布动态数（quanzi_publish 中 userId=userId）
     */
    @GetExchange("/countPublishByUser")
    @GetMapping("/countPublishByUser")
    Long countPublishByUser(@RequestParam("userId") Long userId);

    /**
     * 统计用户粉丝数（follow_user 中 followUserId=userId）
     */
    @GetExchange("/countFollowerByUser")
    @GetMapping("/countFollowerByUser")
    Long countFollowerByUser(@RequestParam("userId") Long userId);

    /**
     * 统计用户关注数（follow_user 中 userId=userId）
     */
    @GetExchange("/countFollowingByUser")
    @GetMapping("/countFollowingByUser")
    Long countFollowingByUser(@RequestParam("userId") Long userId);

    /**
     * 统计用户被喜欢数（user_like 中 likeUserId=userId）
     */
    @GetExchange("/countLikeByUser")
    @GetMapping("/countLikeByUser")
    Long countLikeByUser(@RequestParam("userId") Long userId);

    /**
     * 批量删除动态（含相册、时间线中的记录）
     *
     * @param publishIds 动态ID列表（MongoDB ObjectId 字符串）
     * @return {success: n, failed: m}
     */
    @PostExchange("/batchRemovePublish")
    @PostMapping("/batchRemovePublish")
    Map<String, Object> batchRemovePublish(@RequestBody List<String> publishIds);

    /**
     * 批量删除评论
     *
     * @param commentIds 评论ID列表（MongoDB ObjectId 字符串）
     * @return {success: n, failed: m}
     */
    @PostExchange("/batchRemoveCommentByIds")
    @PostMapping("/batchRemoveCommentByIds")
    Map<String, Object> batchRemoveCommentByIds(@RequestBody List<String> commentIds);
}