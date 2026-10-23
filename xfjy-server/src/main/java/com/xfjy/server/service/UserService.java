package com.xfjy.server.service;

import tools.jackson.databind.json.JsonMapper;
import com.xfjy.server.pojo.User;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class UserService {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserService.class);

    @Autowired
    private RestTemplate restTemplate;

    @Value("${xfjy.sso.url}")
    private String url;

    private static final JsonMapper MAPPER = new JsonMapper();


    /**
     * 调用SSO系统中的接口服务进行查询
     *
     * @param token
     * @return 如果查询到，返回User对象，如果未查询到就反会null
     */
    public User queryUserByToken(String token) {
        String jsonData = this.restTemplate.getForObject(url + "/user/" + token, String.class);
        if(StringUtils.isNotEmpty(jsonData)){
            try {
                return MAPPER.readValue(jsonData, User.class);
            } catch (Exception e) {
                LOGGER.error("Token查询用户失败, token={}", token, e);
            }
        }
        return null;
    }
}
