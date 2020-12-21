package com.tanhua.sso.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Service
public class SmsService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SmsService.class);

    @Resource
    private RestTemplate restTemplate;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Resource
    private RedisTemplate<String, String> redisTemplate;

    public static final String REDIS_KEY_PREFIX = "CHECK_CODE_";

    /**
     * 发送验证码
     *
     * @param mobile=手机号
     * @return
     */
    public Map<String, Object> sendCheckCode(String mobile) {
        Map<String, Object> result = new HashMap<>(2);
        try {
            //CHECK_CODE_17671787093
            String redisKey = "CHECK_CODE_" + mobile;
            //从缓存中，查询当前对象
            String value = this.redisTemplate.opsForValue().get(redisKey);

            if (StringUtils.isNotEmpty(value)) {
                result.put("code", 1);
                result.put("msg", "上一次发送的验证码还未失效");
                return result;
            }
            String code = this.sendSms(mobile);
            //  String code = "123456";
            if (null == code) {
                result.put("code", 2);
                result.put("msg", "发送短信验证码失败");
                return result;
            }
            //发送验证码成功
            result.put("code", 3);
            result.put("msg", "ok");
            System.out.println("验证码：" + result);
            //将验证码存储到Redis,2分钟后失效
            System.out.println("马上要存入Redis；" + redisKey + " ：" + code);
            this.redisTemplate.opsForValue().set(redisKey, code, Duration.ofMinutes(2));

            return result;
        } catch (Exception e) {
            LOGGER.error("发送验证码出错！" + mobile, e);
            result.put("code", 4);
            result.put("msg", "发送验证码出现异常");
            return result;
        }

    }

    /**
     * 调用供应商接口实现验证码短信
     *
     * @param mobile
     */
    public String sendSms(String mobile) {
        String url = "https://open.ucpaas.com/ol/sms/sendsms";
        Map<String, Object> params = new HashMap<>();
        params.put("sid", "56f6523e8f50c85fe92d5d12a8dabd6f1");
        params.put("token", "41fabadd9a221ab4a439548b4dc88433");
        params.put("appid", "dd7d74e604284a6b9cc668c6591c84c7");
        params.put("templateid", "487656");
        params.put("mobile", mobile);
        // 生成6位数验证
        params.put("param", RandomUtils.nextInt(100000, 999999));
        ResponseEntity<String> responseEntity = this.restTemplate.postForEntity(url, params, String.class);

        String body = responseEntity.getBody();
        System.out.println("短信平台响应的参数：" + body);
        try {
            JsonNode jsonNode = MAPPER.readTree(body);
            //000000 表示发送成功
            if (StringUtils.equals(jsonNode.get("code").textValue(), "101111")) {
                return String.valueOf(params.get("param"));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}
