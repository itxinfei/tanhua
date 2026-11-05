package com.xfjy.service.api;

import com.xfjy.service.vo.PageInfo;
import com.xfjy.service.vo.VideoSearchItem;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

/**
 * 小视频搜索内部契约（由 xfjy-video-service 独占 Mongo 实现，BFF 只做编排）
 *
 * <p>独立于 {@link VideoApi}：搜索是新增能力，单独一个契约文件避免改动既有视频接口。</p>
 */
@HttpExchange("/api/video")
public interface VideoSearchApi {

    /**
     * 关键词搜索视频（标题/描述 + 作者昵称命中的作者视频）
     *
     * @param keyword   关键词（已由 BFF 校验长度，域服务内部仍会做正则转义）
     * @param page      页码，从 1 开始
     * @param pageSize  每页条数
     * @param authorIds 昵称命中关键词的作者ID，逗号分隔（可空），由 BFF 通过用户服务解析后传入
     */
    @GetExchange("/search")
    @GetMapping("/search")
    PageInfo<VideoSearchItem> searchVideos(
            @RequestParam("keyword") String keyword,
            @RequestParam("page") Integer page,
            @RequestParam("pageSize") Integer pageSize,
            @RequestParam(value = "authorIds", required = false) String authorIds);
}
