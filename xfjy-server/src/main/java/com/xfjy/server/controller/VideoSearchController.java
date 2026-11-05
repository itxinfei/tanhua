package com.xfjy.server.controller;

import com.xfjy.server.response.Result;
import com.xfjy.server.service.VideoSearchService;
import com.xfjy.server.vo.PageResult;
import com.xfjy.service.vo.HotSearchWord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 小视频搜索接口
 *
 * <p>挂在既有 {@code /small-videos} 前缀下，网关路由与鉴权链路无需改动；
 * 独立 Controller 文件，不改动另一会话正在维护的 {@code VideoController}。</p>
 *
 * <p>响应统一走 {@link Result}，参数/业务错误由 {@code GlobalExceptionHandler} 映射成
 * 400 + {@code {code,message,data:null}}，不另造错误结构。</p>
 */
@RestController
@RequestMapping("small-videos")
public class VideoSearchController {

    private static final Logger LOGGER = LoggerFactory.getLogger(VideoSearchController.class);

    @Autowired
    private VideoSearchService videoSearchService;

    /**
     * 关键词搜索视频
     *
     * @param keyword  关键词（1~30 字）
     * @param page     页码，默认 1
     * @param pageSize 每页条数，默认 10，上限 30
     */
    @GetMapping("/search")
    public ResponseEntity<Result<PageResult>> search(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        LOGGER.info("视频搜索: keyword={}, page={}, pageSize={}", keyword, page, pageSize);
        return ResponseEntity.ok(Result.success(this.videoSearchService.search(keyword, page, pageSize)));
    }

    /**
     * 热搜词（带名次）
     */
    @GetMapping("/search/hot")
    public ResponseEntity<Result<List<HotSearchWord>>> hotWords() {
        return ResponseEntity.ok(Result.success(this.videoSearchService.queryHotWords()));
    }
}
