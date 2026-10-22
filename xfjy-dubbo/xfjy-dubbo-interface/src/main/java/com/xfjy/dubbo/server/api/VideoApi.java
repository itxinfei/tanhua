package com.xfjy.dubbo.server.api;

import com.xfjy.dubbo.server.pojo.Video;
import com.xfjy.dubbo.server.vo.PageInfo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.util.List;

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
     * 根据id查询小视频
     */
    @GetExchange("/queryVideoById")
    @GetMapping("/queryVideoById")
    Video queryVideoById(@RequestParam("id") String id);

    /**
     * 根据vids批量查询视频列表
     */
    @PostExchange("/queryVideoListByPids")
    @PostMapping("/queryVideoListByPids")
    List<Video> queryVideoListByPids(@RequestBody List<Long> vids);
}