package com.xfjy.service.api;

import com.xfjy.service.pojo.Video;
import com.xfjy.service.vo.PageInfo;
import com.xfjy.service.vo.VideoSearchItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 小视频搜索实现（xfjy-video-service 独占 Mongo + Redis 读）
 *
 * <p>匹配策略：video 集合没有独立 title/description 字段，正文统一存在 {@code text}，
 * 因此"标题 + 描述"实际等价于对 {@code text} 做一次不区分大小写的包含匹配；
 * 作者昵称命中由 BFF 先查用户服务解析成 authorIds 再传入，本类不再跨服务调用。</p>
 *
 * <p>排序策略：相关度优先（前缀命中 &gt; 包含命中 &gt; 仅作者命中），
 * 同级再用点赞数、发布时间、ObjectId 三级兜底，保证 (score, likeCount, created, id)
 * 为全序，同一候选池内翻页不会重复/漏项。</p>
 *
 * <p>索引现状：video 集合仅在 {@code userId}/{@code created} 上有业务查询，
 * 未建文本索引（Mongo 每个集合最多一个 text 索引，且中文分词效果差），
 * 故使用正则匹配，用户输入一律经 {@link Pattern#quote(String)} 字面量化，
 * 避免 {@code .*} 等元字符被当作正则执行（正则注入）与回溯爆炸。</p>
 */
@RestController
@RequestMapping("/api/video")
public class VideoSearchApiImpl implements VideoSearchApi {

    private static final Logger LOGGER = LoggerFactory.getLogger(VideoSearchApiImpl.class);

    /** 候选池上限：相关性排序需在内存中完成，超出部分不参与排序（接口返回 total 为池内命中数） */
    private static final int CANDIDATE_LIMIT = 300;

    /** 单次最多解析的作者ID数量，防御超长 authorIds 参数 */
    private static final int AUTHOR_ID_LIMIT = 100;

    /** 关键词硬上限（BFF 已校验，域服务兜底防绕过） */
    private static final int KEYWORD_MAX = 40;

    /** 视频点赞数缓存 key 前缀（与 BFF 侧 QUANZI_COMMENT_LIKE_{videoId} 保持一致） */
    private static final String LIKE_KEY_PREFIX = "QUANZI_COMMENT_LIKE_";

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;

    @Override
    @GetMapping("/search")
    public PageInfo<VideoSearchItem> searchVideos(@RequestParam("keyword") String keyword,
                                                  @RequestParam("page") Integer page,
                                                  @RequestParam("pageSize") Integer pageSize,
                                                  @RequestParam(value = "authorIds", required = false) String authorIds) {
        int p = (page == null || page < 1) ? 1 : page;
        int size = (pageSize == null || pageSize < 1) ? 10 : Math.min(pageSize, 30);

        String kw = keyword == null ? "" : keyword.trim();
        if (kw.isEmpty()) {
            return emptyPage(p, size);
        }
        if (kw.length() > KEYWORD_MAX) {
            kw = kw.substring(0, KEYWORD_MAX);
        }

        List<Long> authorIdList = parseAuthorIds(authorIds);

        // 标题/描述匹配。Pattern.quote 把用户输入整段字面量化：
        // keyword=".*" 时只会匹配包含字面量 ".*" 的文本，不会被当成"匹配任意内容"的正则执行。
        String quoted = Pattern.quote(kw);
        Criteria textCriteria = Criteria.where("text").regex(quoted, "i");
        Criteria matchCriteria = authorIdList.isEmpty()
                ? textCriteria
                : new Criteria().orOperator(textCriteria, Criteria.where("userId").in(authorIdList));
        // 只搜公开视频（seeType=1；历史数据为空时按公开处理），避免把私密作品搜出来
        Criteria visibility = new Criteria().orOperator(
                Criteria.where("seeType").is(1),
                Criteria.where("seeType").is(null));
        // 软删除过滤：已下架视频不参与搜索（ne(true) 同时匹配 false 和 null，旧数据无需迁移）
        Criteria criteria = new Criteria().andOperator(matchCriteria, visibility, Criteria.where("deleted").ne(true));

        Query query = new Query(criteria)
                .with(Sort.by(Sort.Order.desc("created")))
                .limit(CANDIDATE_LIMIT);
        List<Video> candidates = this.mongoTemplate.find(query, Video.class);
        if (candidates == null || candidates.isEmpty()) {
            return emptyPage(p, size);
        }

        Map<String, Long> likeCountMap = loadLikeCounts(candidates);
        String kwLower = kw.toLowerCase();

        // 相关度打分：(视频, 分值) 一一对应，避免往传输对象里塞排序用字段
        Map<String, Integer> scoreMap = new HashMap<>();
        for (Video v : candidates) {
            String textLower = v.getText() == null ? "" : v.getText().toLowerCase();
            int score = 0;
            if (!authorIdList.isEmpty() && authorIdList.contains(v.getUserId())) {
                score += 1;
            }
            if (textLower.startsWith(kwLower)) {
                score += 3;
            } else if (textLower.contains(kwLower)) {
                score += 2;
            }
            scoreMap.put(v.getId().toHexString(), score);
        }

        List<Video> ordered = new ArrayList<>(candidates);
        ordered.sort((a, b) -> {
            String idA = a.getId().toHexString();
            String idB = b.getId().toHexString();
            int byScore = Integer.compare(scoreMap.getOrDefault(idB, 0), scoreMap.getOrDefault(idA, 0));
            if (byScore != 0) {
                return byScore;
            }
            int byLike = Long.compare(likeCountMap.getOrDefault(idB, 0L), likeCountMap.getOrDefault(idA, 0L));
            if (byLike != 0) {
                return byLike;
            }
            long createdA = a.getCreated() == null ? 0L : a.getCreated();
            long createdB = b.getCreated() == null ? 0L : b.getCreated();
            int byCreated = Long.compare(createdB, createdA);
            if (byCreated != 0) {
                return byCreated;
            }
            return idB.compareTo(idA);
        });

        int total = ordered.size();
        int start = (p - 1) * size;
        if (start >= total) {
            return emptyPage(p, size);
        }
        int end = Math.min(start + size, total);

        List<VideoSearchItem> items = new ArrayList<>();
        for (Video v : ordered.subList(start, end)) {
            items.add(toItem(v, likeCountMap));
        }

        PageInfo<VideoSearchItem> pageInfo = new PageInfo<>();
        pageInfo.setPageNum(p);
        pageInfo.setPageSize(size);
        pageInfo.setTotal(total);
        pageInfo.setPages(size > 0 ? ((total + size - 1) / size) : 0);
        pageInfo.setRecords(items);
        return pageInfo;
    }

    /** 批量读取点赞数：优先取 Mongo 落库计数，历史数据缺失（null）时回退 Redis 赞数键 */
    private Map<String, Long> loadLikeCounts(List<Video> videos) {
        Map<String, Long> result = new HashMap<>();
        List<String> fallbackKeys = new ArrayList<>();
        for (Video v : videos) {
            String id = v.getId().toHexString();
            if (v.getLikeCount() != null) {
                result.put(id, Math.max(0L, v.getLikeCount().longValue()));
            } else {
                fallbackKeys.add(id);
            }
        }
        if (fallbackKeys.isEmpty() || this.stringRedisTemplate == null) {
            return result;
        }
        try {
            List<String> redisKeys = new ArrayList<>();
            for (String id : fallbackKeys) {
                redisKeys.add(LIKE_KEY_PREFIX + id);
            }
            List<String> values = this.stringRedisTemplate.opsForValue().multiGet(redisKeys);
            for (int i = 0; i < fallbackKeys.size(); i++) {
                String value = values == null || i >= values.size() ? null : values.get(i);
                long count = 0L;
                if (value != null) {
                    try {
                        count = Long.parseLong(value);
                    } catch (NumberFormatException e) {
                        // 脏数据按 0 处理，避免整页排序失败
                        count = 0L;
                    }
                }
                result.put(fallbackKeys.get(i), count);
            }
        } catch (Exception e) {
            LOGGER.warn("搜索点赞数 Redis 回退读取失败，按 0 兜底", e);
        }
        return result;
    }

    private static VideoSearchItem toItem(Video v, Map<String, Long> likeCountMap) {
        VideoSearchItem item = new VideoSearchItem();
        String videoId = v.getId().toHexString();
        item.setId(videoId);
        item.setVid(v.getVid());
        item.setTitle(v.getText());
        item.setCover(v.getPicUrl());
        item.setAuthorId(v.getUserId());
        item.setAuthorName(null);
        item.setLikeCount((int) Math.min(Integer.MAX_VALUE, likeCountMap.getOrDefault(videoId, 0L)));
        item.setPlayCount(v.getPlayCount() == null ? 0L : v.getPlayCount().longValue());
        return item;
    }

    /** 解析逗号分隔的作者ID，忽略非法项（非数字/空项），最多 AUTHOR_ID_LIMIT 个 */
    private static List<Long> parseAuthorIds(String authorIds) {
        List<Long> list = new ArrayList<>();
        if (StringUtils.isBlank(authorIds)) {
            return list;
        }
        for (String part : StringUtils.split(authorIds, ',')) {
            if (list.size() >= AUTHOR_ID_LIMIT) {
                break;
            }
            try {
                list.add(Long.valueOf(part.trim()));
            } catch (NumberFormatException e) {
                // 非法项直接跳过，不让一个脏值打断整次搜索
            }
        }
        return list;
    }

    private static PageInfo<VideoSearchItem> emptyPage(int page, int size) {
        PageInfo<VideoSearchItem> empty = new PageInfo<>();
        empty.setPageNum(page);
        empty.setPageSize(size);
        empty.setTotal(0);
        empty.setPages(0);
        empty.setRecords(new ArrayList<>());
        return empty;
    }
}
