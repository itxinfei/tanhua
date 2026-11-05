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

    /**
     * 查询「附近」视频：按 geo 2dsphere 球面距离升序（$geoNear），distance 字段回填在 Video 上
     *
     * @param lat      纬度
     * @param lng      经度
     * @param radiusKm 半径（公里）
     */
    @GetExchange("/queryLocationVideoList")
    @GetMapping("/queryLocationVideoList")
    PageInfo<Video> queryLocationVideoList(@RequestParam("page") Integer page,
                                           @RequestParam("pageSize") Integer pageSize,
                                           @RequestParam("lat") Double lat,
                                           @RequestParam("lng") Double lng,
                                           @RequestParam("radiusKm") Double radiusKm);

    /**
     * 查询推荐排序候选池（热度分降序，稳定 (score, id) 排序）
     *
     * <p>热度分 = (点赞×3 + 评论×5 + 播放×1) × 1/(1 + 发布距今小时数/24)</p>
     *
     * @param limit 候选池上限（服务端再按页切分并缓存有序 vid 列表）
     */
    @GetExchange("/queryRecommendVideoList")
    @GetMapping("/queryRecommendVideoList")
    List<Video> queryRecommendVideoList(@RequestParam("limit") Integer limit);

    /**
     * 视频计数字段原子自增
     *
     * @param videoId 视频ID（ObjectId hex）
     * @param field   likeCount / commentCount / playCount
     * @param delta   增量（负值时服务端以 field > 0 为条件，保证不出现负数）
     * @return 是否命中并更新了文档
     */
    @PostExchange("/incVideoCounter")
    @PostMapping("/incVideoCounter")
    Boolean incVideoCounter(@RequestParam("videoId") String videoId,
                            @RequestParam("field") String field,
                            @RequestParam("delta") Integer delta);

    /**
     * 覆盖式写入计数字段（历史数据回填专用，幂等可重跑）
     */
    @PostExchange("/setVideoCounters")
    @PostMapping("/setVideoCounters")
    Boolean setVideoCounters(@RequestParam("videoId") String videoId,
                             @RequestParam("likeCount") Integer likeCount,
                             @RequestParam("commentCount") Integer commentCount);

    /**
     * 回填专用：仅当字段在库中为 null（或缺失）时才写入，已存在的非空值一律不动。
     *
     * @param likeCount    为 null 时表示本次不回填点赞数
     * @param commentCount 为 null 时表示本次不回填评论数
     * @return 是否实际修改了文档（两个字段都已非空或被并发补齐时为 false）
     */
    @PostExchange("/fillVideoCountersIfNull")
    @PostMapping("/fillVideoCountersIfNull")
    Boolean fillVideoCountersIfNull(@RequestParam("videoId") String videoId,
                                    @RequestParam(value = "likeCount", required = false) Integer likeCount,
                                    @RequestParam(value = "commentCount", required = false) Integer commentCount);
}