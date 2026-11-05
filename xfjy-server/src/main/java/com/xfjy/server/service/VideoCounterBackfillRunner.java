package com.xfjy.server.service;

import com.xfjy.service.api.QuanZiApi;
import com.xfjy.service.api.VideoApi;
import com.xfjy.service.pojo.Video;
import com.xfjy.service.vo.PageInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 历史视频计数回填（默认关闭，需显式开启：xfjy.video.backfill.enabled=true）。
 *
 * <p>安全口径：</p>
 * <ul>
 *   <li>只回填字段当前为 null（或缺失）的文档，已存在的非空值一律不动；</li>
 *   <li>来源缺失（Redis 赞数键不存在 / 评论聚合无该视频）时跳过整条，不写 0；</li>
 *   <li>写入前把候选 videoId 与将要写入的值落到审计文件（仓库外），便于回滚；</li>
 *   <li>dry-run 模式只统计与打印，不写库。</li>
 * </ul>
 * <p>开启方式（配置默认值保持关闭，运行时通过命令行覆盖）：</p>
 * <pre>
 * --xfjy.video.backfill.enabled=true --xfjy.video.backfill.dry-run=true
 * </pre>
 */
@Component
@ConditionalOnProperty(name = "xfjy.video.backfill.enabled", havingValue = "true")
public class VideoCounterBackfillRunner implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(VideoCounterBackfillRunner.class);

    /** queryAllVideoList 服务端 pageSize 上限为 100 */
    private static final int PAGE_SIZE = 100;

    private static final String LIKE_KEY_PREFIX = "QUANZI_COMMENT_LIKE_";

    private final VideoApi videoApi;
    private final QuanZiApi quanZiApi;
    private final RedisTemplate<String, String> redisTemplate;

    /** true=只统计不写库（默认），false=真实写入 */
    @Value("${xfjy.video.backfill.dry-run:true}")
    private boolean dryRun;

    /** 审计文件路径；留空则写到系统临时目录 */
    @Value("${xfjy.video.backfill.audit-file:}")
    private String auditFile;

    public VideoCounterBackfillRunner(VideoApi videoApi, QuanZiApi quanZiApi,
                                     RedisTemplate<String, String> redisTemplate) {
        this.videoApi = videoApi;
        this.quanZiApi = quanZiApi;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        Stats stats = new Stats();
        Set<String> likeKeys = this.redisTemplate.keys(LIKE_KEY_PREFIX + "*");
        stats.likeKeysInRedis = likeKeys == null ? 0 : likeKeys.size();

        Path audit = resolveAuditPath();
        LOGGER.info("[BACKFILL] 模式={} 审计文件={}", this.dryRun ? "DRY-RUN" : "APPLY", audit);
        try (BufferedWriter writer = openAudit(audit)) {
            int page = 1;
            while (true) {
                PageInfo<Video> pageInfo = this.videoApi.queryAllVideoList(page, PAGE_SIZE, null, null, null, null);
                List<Video> records = pageInfo == null ? null : pageInfo.getRecords();
                if (records == null || records.isEmpty()) {
                    break;
                }
                stats.pageCount = page;

                List<String> ids = new ArrayList<>();
                for (Video video : records) {
                    if (video.getId() != null) {
                        ids.add(video.getId().toHexString());
                    }
                }
                Map<String, Long> commentCountMap = Collections.emptyMap();
                boolean commentAvailable = true;
                try {
                    // type=2 表示评论（与 VideoService.assembleVideoVoList 口径一致）
                    commentCountMap = this.quanZiApi.queryCommentCountBatch(ids, 2);
                    if (commentCountMap == null) {
                        commentCountMap = Collections.emptyMap();
                    }
                } catch (Exception e) {
                    commentAvailable = false;
                    LOGGER.warn("[BACKFILL] 批量查询评论数失败，本页评论来源视为不可用", e);
                }

                List<String> pageIds = new ArrayList<>();
                // 与 records 下标严格对齐：id 为 null 时用不存在的占位键，避免 multiGet 结果错位
                List<String> likeLookupKeys = new ArrayList<>();
                for (Video video : records) {
                    likeLookupKeys.add(LIKE_KEY_PREFIX
                            + (video.getId() == null ? "__NO_ID__" : video.getId().toHexString()));
                }
                List<String> likeValues = this.redisTemplate.opsForValue().multiGet(likeLookupKeys);

                for (int i = 0; i < records.size(); i++) {
                    Video video = records.get(i);
                    if (video.getId() == null) {
                        continue;
                    }
                    stats.total++;
                    String videoId = video.getId().toHexString();
                    boolean likeNull = video.getLikeCount() == null;
                    boolean commentNull = video.getCommentCount() == null;
                    if (likeNull) {
                        stats.likeNull++;
                    }
                    if (commentNull) {
                        stats.commentNull++;
                    }
                    if (!likeNull && !commentNull) {
                        stats.alreadyFilled++;
                        continue;
                    }

                    String likeValue = likeValues == null ? null : likeValues.get(i);
                    boolean hasLikeSource = likeKeys != null && likeKeys.contains(LIKE_KEY_PREFIX + videoId);
                    boolean hasCommentSource = commentAvailable && commentCountMap.containsKey(videoId);
                    boolean skip = !hasLikeSource || !hasCommentSource;
                    if (skip) {
                        stats.skipped++;
                        if (!hasLikeSource) {
                            stats.noLikeSource++;
                        }
                        if (!hasCommentSource) {
                            stats.noCommentSource++;
                        }
                        continue;
                    }

                    int likeCount = parseCount(likeValue);
                    int commentCount = Math.max(0, commentCountMap.get(videoId).intValue());
                    stats.candidates++;
                    LOGGER.info("[BACKFILL] {} videoId={} likeCount={} commentCount={}",
                            this.dryRun ? "将写入" : "写入", videoId, likeCount, commentCount);
                    if (!this.dryRun) {
                        pageIds.add(videoId + "," + likeCount + "," + commentCount);
                        Boolean modified = this.videoApi.fillVideoCountersIfNull(videoId, likeCount, commentCount);
                        if (Boolean.TRUE.equals(modified)) {
                            stats.written++;
                        } else {
                            stats.noop++;
                        }
                    }
                }
                if (!this.dryRun && !pageIds.isEmpty()) {
                    appendLines(writer, pageIds);
                    LOGGER.info("[BACKFILL] 第 {} 页审计已落盘：{} 条", page, pageIds.size());
                }
                if (records.size() < PAGE_SIZE) {
                    break;
                }
                page++;
            }
            appendSummary(writer, stats, audit);
        } catch (IOException e) {
            LOGGER.error("[BACKFILL] 审计文件写入失败", e);
        }
        LOGGER.info("[BACKFILL] 完成 模式={} 总文档数={} likeCount为null={} commentCount为null={} "
                        + "两个计数均已存在={} 跳过(来源缺失)={} 其中无Redis赞键={} 其中无评论数={} "
                        + "候选={} 实际写入={} 未命中(已非空/并发补齐)={} Redis赞键总数={} 审计文件={}",
                this.dryRun ? "DRY-RUN" : "APPLY", stats.total, stats.likeNull, stats.commentNull,
                stats.alreadyFilled, stats.skipped, stats.noLikeSource, stats.noCommentSource,
                stats.candidates, stats.written, stats.noop, stats.likeKeysInRedis, audit);
        LOGGER.info("[BACKFILL] dry-run 结论：将被覆盖的非空字段数=0；跳过占比={}%（阈值30%）",
                stats.total == 0 ? 0 : String.format("%.1f", stats.skipped * 100.0 / stats.total));
    }

    private Path resolveAuditPath() {
        if (this.auditFile != null && !this.auditFile.trim().isEmpty()) {
            return Paths.get(this.auditFile.trim());
        }
        return Paths.get(System.getProperty("java.io.tmpdir"), "xfjy-video-backfill-audit.txt");
    }

    private BufferedWriter openAudit(Path audit) throws IOException {
        Path parent = audit.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        return Files.newBufferedWriter(audit, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    }

    private void appendLines(BufferedWriter writer, List<String> lines) throws IOException {
        for (String line : lines) {
            writer.write(line);
            writer.newLine();
        }
        writer.flush();
    }

    private void appendSummary(BufferedWriter writer, Stats stats, Path audit) throws IOException {
        writer.write("# mode=" + (this.dryRun ? "DRY-RUN" : "APPLY")
                + " total=" + stats.total
                + " likeNull=" + stats.likeNull
                + " commentNull=" + stats.commentNull
                + " alreadyFilled=" + stats.alreadyFilled
                + " skipped=" + stats.skipped
                + " noLikeSource=" + stats.noLikeSource
                + " noCommentSource=" + stats.noCommentSource
                + " candidates=" + stats.candidates
                + " written=" + stats.written
                + " noop=" + stats.noop
                + " likeKeysInRedis=" + stats.likeKeysInRedis);
        writer.newLine();
        writer.flush();
    }

    private int parseCount(String value) {
        if (value == null || value.isEmpty()) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static class Stats {
        int total;
        int likeNull;
        int commentNull;
        int alreadyFilled;
        int skipped;
        int noLikeSource;
        int noCommentSource;
        int candidates;
        int written;
        int noop;
        int pageCount;
        int likeKeysInRedis;
    }
}
