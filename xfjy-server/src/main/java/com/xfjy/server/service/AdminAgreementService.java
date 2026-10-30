package com.xfjy.server.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xfjy.server.mapper.AgreementMapper;
import com.xfjy.server.pojo.Agreement;
import com.xfjy.server.utils.AdminThreadLocal;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * 协议管理服务（后台 CRUD + C 端读取）
 */
@Service
public class AdminAgreementService {

    @Autowired
    private AgreementMapper agreementMapper;

    public Map<String, Object> queryAgreementPage(Integer page, Integer pageSize,
                                                  String type, String status) {
        QueryWrapper<Agreement> wrapper = new QueryWrapper<>();
        if (StringUtils.isNotBlank(type)) {
            wrapper.eq("type", type);
        }
        if (StringUtils.isNotBlank(status)) {
            wrapper.eq("status", status);
        }
        wrapper.orderByDesc("updated");

        IPage<Agreement> agreementPage = this.agreementMapper.selectPage(new Page<>(page, pageSize), wrapper);

        Map<String, Object> result = new HashMap<>();
        result.put("total", agreementPage.getTotal());
        result.put("list", agreementPage.getRecords());
        return result;
    }

    public Agreement queryAgreementDetail(Long id) {
        return this.agreementMapper.selectById(id);
    }

    public Agreement createAgreement(String type, String title, String content) {
        Agreement agreement = new Agreement();
        agreement.setType(type);
        agreement.setTitle(title);
        agreement.setContent(content);
        agreement.setStatus("DRAFT");
        agreement.setVersion(1);
        agreement.setPublisher(AdminThreadLocal.get() != null ? AdminThreadLocal.get().getNickname() : "admin");
        this.agreementMapper.insert(agreement);
        return agreement;
    }

    public Boolean updateAgreement(Long id, String type, String title, String content) {
        Agreement agreement = this.agreementMapper.selectById(id);
        if (agreement == null) {
            return false;
        }
        if (StringUtils.isNotBlank(type)) {
            agreement.setType(type);
        }
        if (StringUtils.isNotBlank(title)) {
            agreement.setTitle(title);
        }
        if (content != null) {
            agreement.setContent(content);
        }
        return this.agreementMapper.updateById(agreement) > 0;
    }

    public Boolean publishAgreement(Long id) {
        Agreement agreement = this.agreementMapper.selectById(id);
        if (agreement == null) {
            return false;
        }
        if ("PUBLISHED".equals(agreement.getStatus())) {
            return true;
        }
        this.agreementMapper.update(null, new UpdateWrapper<Agreement>()
                .eq("type", agreement.getType())
                .eq("status", "PUBLISHED")
                .set("status", "OFFLINE"));
        Integer nextVersion = (agreement.getVersion() == null ? 1 : agreement.getVersion()) + 1;
        agreement.setStatus("PUBLISHED");
        agreement.setVersion(nextVersion);
        agreement.setPublishedAt(new Date());
        return this.agreementMapper.updateById(agreement) > 0;
    }

    public Boolean offlineAgreement(Long id) {
        Agreement agreement = this.agreementMapper.selectById(id);
        if (agreement == null) {
            return false;
        }
        agreement.setStatus("OFFLINE");
        return this.agreementMapper.updateById(agreement) > 0;
    }

    public Boolean deleteAgreement(Long id) {
        return this.agreementMapper.deleteById(id) > 0;
    }

    public Agreement queryPublishedAgreement(String type) {
        QueryWrapper<Agreement> wrapper = new QueryWrapper<>();
        wrapper.eq("type", type)
                .eq("status", "PUBLISHED")
                .orderByDesc("version")
                .last("LIMIT 1");
        return this.agreementMapper.selectOne(wrapper);
    }
}
