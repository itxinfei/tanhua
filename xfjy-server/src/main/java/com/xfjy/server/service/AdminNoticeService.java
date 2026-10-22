package com.xfjy.server.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xfjy.server.mapper.NoticeMapper;
import com.xfjy.server.pojo.Notice;
import com.xfjy.server.utils.AdminThreadLocal;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 后台公告管理服务
 */
@Service
public class AdminNoticeService {

    @Autowired
    private NoticeMapper noticeMapper;

    /**
     * 分页查询公告
     */
    public Map<String, Object> queryNoticePage(Integer page, Integer pageSize,
                                                String title, String type, String status) {
        QueryWrapper<Notice> wrapper = new QueryWrapper<>();
        if (StringUtils.isNotBlank(title)) {
            wrapper.like("title", title);
        }
        if (StringUtils.isNotBlank(type)) {
            wrapper.eq("type", type);
        }
        if (StringUtils.isNotBlank(status)) {
            wrapper.eq("status", status);
        }
        wrapper.orderByDesc("created");

        IPage<Notice> noticePage = this.noticeMapper.selectPage(new Page<>(page, pageSize), wrapper);

        Map<String, Object> result = new HashMap<>();
        result.put("total", noticePage.getTotal());
        result.put("list", noticePage.getRecords());
        return result;
    }

    /**
     * 查询公告详情
     */
    public Notice queryNoticeDetail(Long id) {
        return this.noticeMapper.selectById(id);
    }

    /**
     * 新增公告（草稿）
     */
    public Notice createNotice(String title, String content, String type) {
        Notice notice = new Notice();
        notice.setTitle(title);
        notice.setContent(content);
        notice.setType(type);
        notice.setStatus("DRAFT");
        notice.setPublisher(AdminThreadLocal.get() != null ? AdminThreadLocal.get().getNickname() : "admin");
        this.noticeMapper.insert(notice);
        return notice;
    }

    /**
     * 更新公告
     */
    public Boolean updateNotice(Long id, String title, String content, String type) {
        Notice notice = this.noticeMapper.selectById(id);
        if (notice == null) {
            return false;
        }
        if (StringUtils.isNotBlank(title)) {
            notice.setTitle(title);
        }
        if (content != null) {
            notice.setContent(content);
        }
        if (StringUtils.isNotBlank(type)) {
            notice.setType(type);
        }
        return this.noticeMapper.updateById(notice) > 0;
    }

    /**
     * 发布公告
     */
    public Boolean publishNotice(Long id) {
        Notice notice = this.noticeMapper.selectById(id);
        if (notice == null) {
            return false;
        }
        notice.setStatus("PUBLISHED");
        notice.setPublishedAt(new Date());
        return this.noticeMapper.updateById(notice) > 0;
    }

    /**
     * 下线公告
     */
    public Boolean offlineNotice(Long id) {
        Notice notice = this.noticeMapper.selectById(id);
        if (notice == null) {
            return false;
        }
        notice.setStatus("OFFLINE");
        return this.noticeMapper.updateById(notice) > 0;
    }

    /**
     * 删除公告
     */
    public Boolean deleteNotice(Long id) {
        return this.noticeMapper.deleteById(id) > 0;
    }
}