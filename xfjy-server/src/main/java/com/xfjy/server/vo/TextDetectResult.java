package com.xfjy.server.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 文本敏感词检测结果
 *
 * <p>action 取值：
 * <ul>
 *   <li>PASS —— 未命中敏感词，原样放行</li>
 *   <li>FILTERED —— 仅命中 LOW(3) 级词，已替换为星号，filteredText 为替换后文本</li>
 *   <li>MANUAL —— 命中 MID(2) 级词，内容放行但需进人工审核队列</li>
 *   <li>BLOCK —— 命中 HIGH(1) 级词，直接拒绝发布</li>
 * </ul></p>
 */
@Data
public class TextDetectResult {

    /** 审核动作 */
    private String action;

    /** LOW 级词替换为星号后的文本（action=FILTERED 时与原文不同，其余与原文一致） */
    private String filteredText;

    /** 命中的敏感词列表 */
    private List<String> hitWords = new ArrayList<>();

    /** 命中的最高级别（1-HIGH 2-MID 3-LOW，0 表示未命中） */
    private int maxLevel;

    public static TextDetectResult pass(String text) {
        TextDetectResult result = new TextDetectResult();
        result.setAction("PASS");
        result.setFilteredText(text);
        result.setMaxLevel(0);
        return result;
    }
}