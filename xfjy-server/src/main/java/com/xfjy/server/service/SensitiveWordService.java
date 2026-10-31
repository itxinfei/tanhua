package com.xfjy.server.service;

import com.github.houbb.sensitive.word.bs.SensitiveWordBs;
import com.github.houbb.sensitive.word.support.deny.WordDenys;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.xfjy.server.mapper.SensitiveWordMapper;
import com.xfjy.server.pojo.SensitiveWord;
import com.xfjy.server.vo.TextDetectResult;
import jakarta.annotation.PostConstruct;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 敏感词检测服务（基于 houbb sensitive-word 引擎）
 *
 * <p>词库 = 引擎内置词库 + tb_sensitive_word 启用词（管理端维护）。
 * 内置词命中默认按 MID 级处理（进人工审核不误杀）；管理端词按其 level 定级。
 * 词库变更后调用 {@link #refresh()} 重建引擎。</p>
 *
 * <p>级别策略：HIGH(1) 直接拒绝 → MID(2) 进人工审核 → LOW(3) 替换星号放行</p>
 */
@Service
public class SensitiveWordService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SensitiveWordService.class);

    /** LOW 级词替换用的占位符 */
    private static final char MASK_CHAR = '*';

    /** 引擎内置词（不在管理端词库中）命中时的默认级别：MID */
    private static final int DEFAULT_BUILTIN_LEVEL = 2;

    @Autowired
    private SensitiveWordMapper sensitiveWordMapper;

    /** 敏感词引擎实例，volatile 保证刷新后对检测线程立即可见 */
    private volatile SensitiveWordBs wordBs;

    /** 管理端词 → 级别 映射，用于对命中词定级 */
    private volatile Map<String, Integer> wordLevelMap = new HashMap<>();

    /**
     * 启动时加载敏感词并构建引擎
     */
    @PostConstruct
    public void init() {
        refresh();
    }

    /**
     * 重新加载敏感词库并重建检测引擎（管理端维护词库后调用）
     */
    public void refresh() {
        QueryWrapper<SensitiveWord> wrapper = new QueryWrapper<>();
        wrapper.eq("status", 1);
        List<SensitiveWord> words = this.sensitiveWordMapper.selectList(wrapper);

        Map<String, Integer> levelMap = new HashMap<>();
        for (SensitiveWord word : words) {
            levelMap.put(word.getWord(), word.getLevel());
        }

        List<String> dbWords = new ArrayList<>(levelMap.keySet());
        // 关闭数字/邮箱/网址/IPv4 形态检测，只保留词库命中，行为与原词树检测对齐
        this.wordBs = SensitiveWordBs.newInstance()
                .wordDeny(WordDenys.chains(WordDenys.defaults(), () -> dbWords))
                .enableNumCheck(false)
                .enableEmailCheck(false)
                .enableUrlCheck(false)
                .enableIpv4Check(false)
                .init();
        this.wordLevelMap = levelMap;
        LOGGER.info("敏感词引擎重建完成，管理端词 {} 个（另有引擎内置词库）", dbWords.size());
    }

    /**
     * 检测文本中的敏感词
     *
     * @param text 待检测文本，blank 时直接返回 PASS
     * @return 检测结果（含 action/filteredText/hitWords/maxLevel）
     */
    public TextDetectResult checkText(String text) {
        if (StringUtils.isBlank(text)) {
            return TextDetectResult.pass(text);
        }

        List<String> hits = this.wordBs.findAll(text);

        TextDetectResult result = new TextDetectResult();
        if (hits.isEmpty()) {
            result.setAction("PASS");
            result.setFilteredText(text);
            result.setMaxLevel(0);
            result.setHitWords(new ArrayList<>());
            return result;
        }

        int maxLevel = Integer.MAX_VALUE;
        List<String> lowWords = new ArrayList<>();
        for (String hit : hits) {
            int level = this.wordLevelMap.getOrDefault(hit, DEFAULT_BUILTIN_LEVEL);
            maxLevel = Math.min(maxLevel, level);
            if (level == 3) {
                lowWords.add(hit);
            }
        }
        // 引擎归一化匹配（大小写/全半角/防混淆）后返回的词形可能与原文不一致，仅对能在原文定位的词做星号替换
        lowWords.removeIf(word -> !text.contains(word));

        result.setHitWords(hits);
        result.setMaxLevel(maxLevel);
        if (maxLevel == 1) {
            result.setAction("BLOCK");
            result.setFilteredText(text);
        } else if (maxLevel == 2) {
            result.setAction("MANUAL");
            result.setFilteredText(applyMask(text, lowWords));
        } else {
            result.setAction("FILTERED");
            result.setFilteredText(applyMask(text, lowWords));
        }
        return result;
    }

    /**
     * 将命中的 LOW 级词替换为等长星号
     */
    private String applyMask(String text, List<String> lowWords) {
        String masked = text;
        for (String word : lowWords) {
            masked = masked.replace(word, String.valueOf(MASK_CHAR).repeat(word.length()));
        }
        return masked;
    }
}
