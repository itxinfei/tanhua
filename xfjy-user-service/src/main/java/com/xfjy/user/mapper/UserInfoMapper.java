package com.xfjy.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xfjy.dubbo.server.pojo.UserInfo;

/**
 * 用户信息 Mapper（tb_user_info 由 user-service 独占）
 */
public interface UserInfoMapper extends BaseMapper<UserInfo> {
}