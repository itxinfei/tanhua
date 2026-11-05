package com.xfjy.server.service;

import com.xfjy.server.exception.BusinessException;
import com.xfjy.server.vo.PageResult;
import com.xfjy.server.vo.VideoSearchVo;
import com.xfjy.service.api.UserInfoApi;
import com.xfjy.service.api.VideoSearchApi;
import com.xfjy.service.pojo.UserInfo;
import com.xfjy.service.vo.HotSearchWord;
import com.xfjy.service.vo.PageInfo;
import com.xfjy.service.vo.UserInfoQueryParam;
import com.xfjy.service.vo.VideoSearchItem;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 视频搜索编排服务（BFF 层）
 *
 * <p>职责：参数校验 → 用关键词在用户服务里解析"昵称命中的作者ID" → 调视频域服务搜索
 * → 批量补齐作者昵称。Mongo/Redis 的匹配与排序全部下沉到 xfjy-video-service，
 * 本类不直接访问数据库，避免和另一条正在改 VideoService 的会话产生冲突。</p>
 */
@Service
public class VideoSearchService {

    private static final Logger LOGGER = LoggerFactory.getLogger(VideoSearchService.class);

    /** 关键词长度上限（按字符计，中文按 1 计） */
    private static final int KEYWORD_MAX = 30;

    /** 每页条数上限 */
    private static final int PAGE_SIZE_MAX = 30;

    /** 页码上限：防止超大页码触发无意义的内存分页 */
    private static final int PAGE_MAX = 50;

    /** 昵称命中解析出的作者ID上限，避免 authorIds 参数过长 */
    private static final int AUTHOR_MATCH_LIMIT = 50;

    /**
     * 热搜词兜底常量。
     * 当前没有热搜统计表（点击日志未采集），先用产品运营给定的固定词条；
     * 后续接入 tb_video_search_hot 表时，替换本方法实现即可，接口契约不变。
     */
    private static final List<String> FALLBACK_HOT_WORDS = List.of(
            "交友", "同城", "唱歌", "舞蹈", "旅行", "美食", "宠物", "健身", "日常", "治愈");

    @Autowired
    private VideoSearchApi videoSearchApi;

    @Autowired
    private UserInfoApi userInfoApi;

    /**
     * 搜索视频
     *
     * @param keyword  用户输入关键词
     * @param page     页码（1 开始）
     * @param pageSize 每页条数
     * @return 分页结果（items 为 VideoSearchVo）
     */
    public PageResult search(String keyword, Integer page, Integer pageSize) {
        String kw = this.normalizeKeyword(keyword);
        int p = (page == null || page < 1) ? 1 : Math.min(page, PAGE_MAX);
        int size = (pageSize == null || pageSize < 1) ? 10 : Math.min(pageSize, PAGE_SIZE_MAX);

        List<Long> authorIds = this.resolveAuthorIds(kw);
        String authorIdParam = StringUtils.join(authorIds, ",");

        PageInfo<VideoSearchItem> info = this.videoSearchApi.searchVideos(kw, p, size,
                authorIdParam.isEmpty() ? null : authorIdParam);

        PageResult pageResult = new PageResult();
        pageResult.setPage(p);
        pageResult.setPagesize(size);
        if (info == null) {
            pageResult.setCounts(0);
            pageResult.setPages(0);
            pageResult.setItems(new ArrayList<>());
            return pageResult;
        }

        List<VideoSearchItem> records = info.getRecords() == null ? new ArrayList<>() : info.getRecords();
        pageResult.setCounts(info.getTotal() == null ? 0 : info.getTotal());
        pageResult.setPages(info.getPages() == null ? 0 : info.getPages());

        List<VideoSearchVo> items = new ArrayList<>(records.size());
        for (VideoSearchItem record : records) {
            VideoSearchVo vo = new VideoSearchVo();
            vo.setId(record.getId());
            vo.setVid(record.getVid());
            vo.setTitle(record.getTitle());
            vo.setCover(record.getCover());
            vo.setAuthorId(record.getAuthorId());
            vo.setAuthorName(record.getAuthorName());
            vo.setLikeCount(record.getLikeCount() == null ? 0 : record.getLikeCount());
            vo.setPlayCount(record.getPlayCount());
            items.add(vo);
        }
        this.fillAuthorNames(items);
        pageResult.setItems(items);
        return pageResult;
    }

    /**
     * 热搜词（带名次）
     *
     * <p>当前走常量兜底；接口契约与真实榜单一致，后续接表无需前端改动。</p>
     */
    public List<HotSearchWord> queryHotWords() {
        List<HotSearchWord> list = new ArrayList<>(FALLBACK_HOT_WORDS.size());
        int rank = 1;
        for (String word : FALLBACK_HOT_WORDS) {
            list.add(new HotSearchWord(rank++, word, null));
        }
        return list;
    }

    /** 关键词校验：去空白、限长；空关键词直接 400（调用方不应提交空词，属参数错误） */
    private String normalizeKeyword(String keyword) {
        String kw = StringUtils.trimToEmpty(keyword);
        // 去掉中间连续空白，避免"多 个 空格"这种输入匹配不到任何文本
        kw = kw.replaceAll("\\s+", " ");
        if (kw.isEmpty()) {
            throw new BusinessException(400, "搜索关键词不能为空");
        }
        if (kw.length() > KEYWORD_MAX) {
            throw new BusinessException(400, "搜索关键词最多 " + KEYWORD_MAX + " 个字");
        }
        return kw;
    }

    /**
     * 昵称命中关键词的作者ID列表。
     * 用户服务不可用时降级为"只搜视频正文"，不阻断搜索。
     */
    private List<Long> resolveAuthorIds(String keyword) {
        List<Long> authorIds = new ArrayList<>();
        try {
            UserInfoQueryParam param = new UserInfoQueryParam();
            param.setNickName(keyword);
            List<UserInfo> users = this.userInfoApi.queryUserInfoList(param);
            if (users != null) {
                for (UserInfo user : users) {
                    if (user.getUserId() != null) {
                        authorIds.add(user.getUserId());
                    }
                    if (authorIds.size() >= AUTHOR_MATCH_LIMIT) {
                        break;
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("按昵称解析作者失败，本次搜索仅匹配视频正文: keyword={}", keyword, e);
        }
        return authorIds;
    }

    /** 批量补齐作者昵称（用户服务异常时留空，前端回退为"用户"占位） */
    private void fillAuthorNames(List<VideoSearchVo> items) {
        if (items.isEmpty()) {
            return;
        }
        Set<Long> authorIds = new LinkedHashSet<>();
        for (VideoSearchVo item : items) {
            if (item.getAuthorId() != null) {
                authorIds.add(item.getAuthorId());
            }
        }
        if (authorIds.isEmpty()) {
            return;
        }
        try {
            UserInfoQueryParam param = new UserInfoQueryParam();
            param.setUserIds(new ArrayList<>(authorIds));
            List<UserInfo> users = this.userInfoApi.queryUserInfoList(param);
            Map<Long, String> nameMap = new HashMap<>();
            if (users != null) {
                for (UserInfo user : users) {
                    if (user.getUserId() != null) {
                        nameMap.put(user.getUserId(), user.getNickName());
                    }
                }
            }
            for (VideoSearchVo item : items) {
                String name = nameMap.get(item.getAuthorId());
                if (StringUtils.isNotBlank(name)) {
                    item.setAuthorName(name);
                }
            }
        } catch (Exception e) {
            LOGGER.warn("补全视频作者昵称失败，作者名留空: size={}", items.size(), e);
        }
    }
}
