package com.xfjy.server.service;

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
 * 敏感词检测服务（DFA 算法）
 *
 * <p>启动时从 tb_sensitive_word 加载启用词构建 DFA 树，检测 O(n) 复杂度。
 * 词库变更后调用 {@link #refresh()} 重新加载。</p>
 *
 * <p>级别策略：HIGH(1) 直接拒绝 → MID(2) 进人工审核 → LOW(3) 替换星号放行</p>
 */
@Service
public class SensitiveWordService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SensitiveWordService.class);

    /** DFA 树叶子节点的结束标记 key（null 字符不会出现在正常文本中），value 为该词的 level */
    private static final char END_MARK = '\0';

    /** LOW 级词替换用的占位符 */
    private static final char MASK_CHAR = '*';

    @Autowired
    private SensitiveWordMapper sensitiveWordMapper;

    /** DFA 敏感词树，volatile 保证刷新后对检测线程立即可见 */
    private volatile Map<Character, Object> wordTree = new HashMap<>();

    /**
     * 启动时加载敏感词并构建 DFA 树
     */
    @PostConstruct
    public void init() {
        refresh();
    }

    /**
     * 重新加载敏感词库并重建 DFA 树（管理端维护词库后调用）
     */
    public void refresh() {
        QueryWrapper<SensitiveWord> wrapper = new QueryWrapper<>();
        wrapper.eq("status", 1);
        List<SensitiveWord> words = this.sensitiveWordMapper.selectList(wrapper);
        this.wordTree = buildTree(words);
        LOGGER.info("敏感词库加载完成，共 {} 个词", words.size());
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

        List<int[]> lowHits = new ArrayList<>();
        List<String> hitWords = new ArrayList<>();
        int maxLevel = 0;
        char[] chars = text.toCharArray();

        for (int i = 0; i < chars.length; i++) {
            Map<Character, Object> current = this.wordTree;
            int j = i;
            int lastEnd = -1;
            int lastLevel = 0;

            while (j < chars.length) {
                Object next = current.get(chars[j]);
                if (!(next instanceof Map)) {
                    break;
                }
                current = (Map<Character, Object>) next;
                j++;
                Object endLevel = current.get(END_MARK);
                if (endLevel instanceof Integer level) {
                    lastEnd = j;
                    lastLevel = level;
                }
            }

            if (lastEnd > i) {
                String hitWord = text.substring(i, lastEnd);
                if (!hitWords.contains(hitWord)) {
                    hitWords.add(hitWord);
                }
                maxLevel = Math.max(maxLevel, lastLevel);
                if (lastLevel == 3) {
                    lowHits.add(new int[]{i, lastEnd});
                }
                i = lastEnd - 1;
            }
        }

        TextDetectResult result = new TextDetectResult();
        result.setHitWords(hitWords);
        result.setMaxLevel(maxLevel);

        if (maxLevel == 0) {
            result.setAction("PASS");
            result.setFilteredText(text);
        } else if (maxLevel == 1) {
            result.setAction("BLOCK");
            result.setFilteredText(text);
        } else if (maxLevel == 2) {
            result.setAction("MANUAL");
            result.setFilteredText(applyMask(text, lowHits));
        } else {
            result.setAction("FILTERED");
            result.setFilteredText(applyMask(text, lowHits));
        }
        return result;
    }

    /**
     * 构建 DFA 敏感词树
     */
    private Map<Character, Object> buildTree(List<SensitiveWord> words) {
        Map<Character, Object> root = new HashMap<>();
        for (SensitiveWord word : words) {
            Map<Character, Object> current = root;
            for (char c : word.getWord().toCharArray()) {
                Object next = current.get(c);
                if (!(next instanceof Map)) {
                    next = new HashMap<>();
                    current.put(c, next);
                }
                current = (Map<Character, Object>) next;
            }
            current.put(END_MARK, word.getLevel());
        }
        return root;
    }

    /**
     * 将命中区间替换为等长星号
     */
    private String applyMask(String text, List<int[]> ranges) {
        char[] chars = text.toCharArray();
        for (int[] range : ranges) {
            for (int i = range[0]; i < range[1]; i++) {
                chars[i] = MASK_CHAR;
            }
        }
        return new String(chars);
    }
}