package com.xfjy.service.api;

import com.xfjy.service.pojo.Video;
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

@HttpExchange("/api/video")
public interface VideoApi {

    /**
     * 保存小视频
     */
    @PostExchange("/saveVideo")
    @PostMapping("/saveVideo")
    String saveVideo(@RequestBody Video video);

    /**
     * 分页查询小视频列表，按照时间倒序排序
     */
    @GetExchange("/queryVideoList")
    @GetMapping("/queryVideoList")
    PageInfo<Video> queryVideoList(@RequestParam("page") Integer page,
                                   @RequestParam("pageSize") Integer pageSize);

    /**
     * 关注用户
     */
    @PostExchange("/followUser")
    @PostMapping("/followUser")
    Boolean followUser(@RequestParam("userId") Long userId,
                       @RequestParam("followUserId") Long followUserId);

    /**
     * 取消关注用户
     */
    @PostExchange("/disFollowUser")
    @PostMapping("/disFollowUser")
    Boolean disFollowUser(@RequestParam("userId") Long userId,
                          @RequestParam("followUserId") Long followUserId);

    /**
     * 根据vids批量查询视频列表
     */
    @PostExchange("/queryVideoListByPids")
    @PostMapping("/queryVideoListByPids")
    List<Video> queryVideoListByPids(@RequestBody List<Long> vids);

    /**
     * 记录视频操作评分事件（type：2-点赞，3-取消点赞，4-评论），
     * 由视频服务内部发送 xfjy-video 消息（发布事件 type=1 在 saveVideo 内部直接发送）
     */
    @PostExchange("/recordVideoMsg")
    @PostMapping("/recordVideoMsg")
    Boolean recordVideoMsg(@RequestParam("userId") Long userId,
                           @RequestParam("videoId") String videoId,
                           @RequestParam("type") Integer type);

    /**
     * 删除小视频（内容审核驳回时联动下架）
     *
     * @param videoId 视频ID
     * @return 是否删除成功（视频不存在返回 false）
     */
    @PostExchange("/removeVideo")
    @PostMapping("/removeVideo")
    boolean removeVideo(@RequestParam("videoId") String videoId);

    /**
     * 管理后台全量分页查询视频（按创建时间倒序，支持关键词/用户ID/日期范围筛选）
     *
     * @param page     页码（从1开始，默认1）
     * @param pageSize 每页条数（默认10）
     * @param keyword  文字内容关键词（模糊匹配，可选）
     * @param userId   发布者用户ID（可选）
     * @param startTime 开始时间戳（可选，毫秒）
     * @param endTime   结束时间戳（可选，毫秒）
     * @return 分页结果（含总条数）
     */
    @GetExchange("/queryAllVideoList")
    @GetMapping("/queryAllVideoList")
    PageInfo<Video> queryAllVideoList(@RequestParam(value = "page", required = false) Integer page,
                                       @RequestParam(value = "pageSize", required = false) Integer pageSize,
                                       @RequestParam(value = "keyword", required = false) String keyword,
                                       @RequestParam(value = "userId", required = false) Long userId,
                                       @RequestParam(value = "startTime", required = false) Long startTime,
                                       @RequestParam(value = "endTime", required = false) Long endTime);

    /**
     * 查询用户关注的用户ID列表
     */
    @GetExchange("/queryFollowUserIds")
    @GetMapping("/queryFollowUserIds")
    List<Long> queryFollowUserIds(@RequestParam("userId") Long userId);

    /**
     * 按作者用户ID列表分页查询视频（关注流使用）
     */
    @GetExchange("/queryVideoListByUserIds")
    @GetMapping("/queryVideoListByUserIds")
    PageInfo<Video> queryVideoListByUserIds(@RequestParam("userIds") List<Long> userIds,
                                            @RequestParam("page") Integer page,
                                            @RequestParam("pageSize") Integer pageSize);

    /**
     * 批量删除视频
     *
     * @param videoIds 视频ID列表（MongoDB ObjectId 字符串）
     * @return {success: n, failed: m}
     */
    @PostExchange("/batchRemoveVideo")
    @PostMapping("/batchRemoveVideo")
    Map<String, Object> batchRemoveVideo(@RequestBody List<String> videoIds);
}