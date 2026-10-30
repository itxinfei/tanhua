package com.xfjy.server.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xfjy.server.mapper.SensitiveWordMapper;
import com.xfjy.server.pojo.SensitiveWord;
import com.xfjy.server.vo.TextDetectResult;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 后台敏感词管理服务
 *
 * <p>词库变更（新增/编辑/启停/删除）后自动调用 {@link SensitiveWordService#refresh()}
 * 重建 DFA 检测树，保证线上检测实时生效。</p>
 */
@Service
public class AdminSensitiveWordService {

    @Autowired
    private SensitiveWordMapper sensitiveWordMapper;

    @Autowired
    private SensitiveWordService sensitiveWordService;

    /**
     * 分页查询敏感词
     */
    public Map<String, Object> queryWordPage(Integer page, Integer pageSize,
                                              String word, String category, Integer level, Integer status) {
        QueryWrapper<SensitiveWord> wrapper = new QueryWrapper<>();
        if (StringUtils.isNotBlank(word)) {
            wrapper.like("word", word);
        }
        if (StringUtils.isNotBlank(category)) {
            wrapper.eq("category", category);
        }
        if (level != null) {
            wrapper.eq("level", level);
        }
        if (status != null) {
            wrapper.eq("status", status);
        }
        wrapper.orderByDesc("updated");

        IPage<SensitiveWord> wordPage = this.sensitiveWordMapper.selectPage(new Page<>(page, pageSize), wrapper);

        Map<String, Object> result = new HashMap<>();
        result.put("total", wordPage.getTotal());
        result.put("list", wordPage.getRecords());
        return result;
    }

    /**
     * 新增敏感词（word 唯一，重复返回 false）
     */
    public Boolean createWord(String word, String category, Integer level) {
        if (StringUtils.isBlank(word) || StringUtils.isBlank(category) || level == null) {
            return false;
        }
        QueryWrapper<SensitiveWord> wrapper = new QueryWrapper<>();
        wrapper.eq("word", word);
        if (this.sensitiveWordMapper.selectCount(wrapper) > 0) {
            return false; // 词已存在
        }
        SensitiveWord entity = new SensitiveWord();
        entity.setWord(word);
        entity.setCategory(category);
        entity.setLevel(level);
        entity.setStatus(1);
        entity.setCreated(new Date());
        entity.setUpdated(new Date());
        boolean success = this.sensitiveWordMapper.insert(entity) > 0;
        if (success) {
            this.sensitiveWordService.refresh();
        }
        return success;
    }

    /**
     * 编辑敏感词（word/category/level）
     */
    public Boolean updateWord(Long id, String word, String category, Integer level) {
        SensitiveWord entity = this.sensitiveWordMapper.selectById(id);
        if (entity == null) {
            return false;
        }
        if (StringUtils.isNotBlank(word)) {
            // 改词时查重（排除自身）
            QueryWrapper<SensitiveWord> wrapper = new QueryWrapper<>();
            wrapper.eq("word", word).ne("id", id);
            if (this.sensitiveWordMapper.selectCount(wrapper) > 0) {
                return false;
            }
            entity.setWord(word);
        }
        if (StringUtils.isNotBlank(category)) {
            entity.setCategory(category);
        }
        if (level != null) {
            entity.setLevel(level);
        }
        entity.setUpdated(new Date());
        boolean success = this.sensitiveWordMapper.updateById(entity) > 0;
        if (success) {
            this.sensitiveWordService.refresh();
        }
        return success;
    }

    /**
     * 启用/停用敏感词
     */
    public Boolean toggleWord(Long id, Integer status) {
        SensitiveWord entity = this.sensitiveWordMapper.selectById(id);
        if (entity == null || status == null) {
            return false;
        }
        entity.setStatus(status);
        entity.setUpdated(new Date());
        boolean success = this.sensitiveWordMapper.updateById(entity) > 0;
        if (success) {
            this.sensitiveWordService.refresh();
        }
        return success;
    }

    /**
     * 删除敏感词
     */
    public Boolean deleteWord(Long id) {
        boolean success = this.sensitiveWordMapper.deleteById(id) > 0;
        if (success) {
            this.sensitiveWordService.refresh();
        }
        return success;
    }

    /**
     * 手动刷新 DFA 词库
     */
    public Boolean refresh() {
        this.sensitiveWordService.refresh();
        return true;
    }

    /**
     * 敏感词命中测试
     *
     * @param text 待检测文本
     * @return {action, filteredText, hitWords, maxLevel}
     */
    public TextDetectResult testSensitiveWord(String text) {
        return this.sensitiveWordService.checkText(text);
    }

    /**
     * 批量导入敏感词
     *
     * @param words 敏感词列表（每行一个词）
     * @param category 分类（POLITICS/PORN/VIOLENCE/AD/OTHER）
     * @param level 级别（1/2/3）
     * @return {total, success, failed}
     */
    public Map<String, Object> batchImportWords(List<String> words, String category, Integer level) {
        if (words == null || words.isEmpty()) {
            Map<String, Object> empty = new HashMap<>();
            empty.put("total", 0);
            empty.put("success", 0);
            empty.put("failed", 0);
            return empty;
        }

        int success = 0;
        int failed = 0;
        for (String word : words) {
            String trimmed = word.trim();
            if (StringUtils.isBlank(trimmed)) {
                failed++;
                continue;
            }
            try {
                Boolean result = createWord(trimmed, category, level);
                if (result) {
                    success++;
                } else {
                    failed++;
                }
            } catch (Exception e) {
                failed++;
            }
        }

        // 导入完成后刷新词库
        if (success > 0) {
            this.sensitiveWordService.refresh();
        }

        Map<String, Object> result = new HashMap<>();
        result.put("total", words.size());
        result.put("success", success);
        result.put("failed", failed);
        return result;
    }

    /**
     * 导出敏感词列表（返回全部数据，不分页）
     */
    public List<SensitiveWord> exportWords() {
        QueryWrapper<SensitiveWord> wrapper = new QueryWrapper<>();
        wrapper.orderByDesc("updated");
        return this.sensitiveWordMapper.selectList(wrapper);
    }
}