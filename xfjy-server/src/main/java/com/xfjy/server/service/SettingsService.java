package com.xfjy.server.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.xfjy.server.mapper.SettingsMapper;
import com.xfjy.server.pojo.Settings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SettingsService {

    @Autowired
    private SettingsMapper settingsMapper;

    public Settings querySettingsByUserId(Long userId) {
        QueryWrapper<Settings> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId);
        return this.settingsMapper.selectOne(queryWrapper);
    }

    public void updateNotification(Long userId, Boolean likeNotification, Boolean pinglunNotification, Boolean gonggaoNotification, Boolean matchNotification) {
        Settings settings = new Settings();
        settings.setLikeNotification(likeNotification);
        settings.setGonggaoNotification(gonggaoNotification);
        settings.setPinglunNotification(pinglunNotification);
        settings.setMatchNotification(matchNotification);

        QueryWrapper<Settings> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId);

        this.settingsMapper.update(settings, queryWrapper);

    }
}
