package com.xfjy.server.service;

import tools.jackson.databind.json.JsonMapper;
import com.xfjy.server.pojo.User;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
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
        // token 通过 X-Token 请求头传递，避免出现在 URL 路径中被访问日志记录
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Token", token);
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        ResponseEntity<String> response = this.restTemplate.exchange(url + "/user/query", HttpMethod.GET, entity, String.class);
        String jsonData = response.getBody();
        if(StringUtils.isNotEmpty(jsonData)){
            try {
                return MAPPER.readValue(jsonData, User.class);
            } catch (Exception e) {
                LOGGER.error("Token查询用户失败, token={}", maskToken(token), e);
            }
        }
        return null;
    }

    /**
     * token 脱敏：仅保留前 4 后 4 位，防止完整 token 泄露到日志
     */
    private static String maskToken(String token) {
        if (token == null || token.length() <= 8) {
            return "***";
        }
        return token.substring(0, 4) + "***" + token.substring(token.length() - 4);
    }
}
