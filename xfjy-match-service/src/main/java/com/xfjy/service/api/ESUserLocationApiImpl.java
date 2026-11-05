package com.xfjy.service.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import com.xfjy.service.vo.UserLocationVo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ES版用户地理位置实现（原Dubbo version=2.0.0）
 */
@RestController
@RequestMapping("/api/es-user-location")
public class ESUserLocationApiImpl implements ESUserLocationApi {

    private static final Logger log = LoggerFactory.getLogger(ESUserLocationApiImpl.class);

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Autowired
    private RestTemplate restTemplate;

    // 修改点：ES 地址配置化，未配置时回退本地默认（与 application-local.yml 保持一致）
    // ES 服务（xfjy-elasticsearch）无 context-path，地址应以 / 结尾，如 http://127.0.0.1:18088/
    @Value("${es.server.url:http://127.0.0.1:18088/}")
    private String esServerUrl;

    @Override
    @PostMapping("/updateUserLocation")
    public void updateUserLocation(@RequestBody Map<String, Object> param) {
        try {
            String url = this.esServerUrl + "api/es-user-location/updateUserLocation";

            String requestBody = MAPPER.writeValueAsString(param);

            HttpHeaders httpHeaders = new HttpHeaders();
            httpHeaders.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> httpEntity = new HttpEntity<>(requestBody, httpHeaders);

            ResponseEntity<Void> responseEntity = this.restTemplate.postForEntity(url, httpEntity, Void.class);
            if (responseEntity.getStatusCode().value() != 200) {
                log.warn("ES updateUserLocation 非 200 响应, esServerUrl={}, status={}",
                        esServerUrl, responseEntity.getStatusCode().value());
            }
        } catch (Exception e) {
            // 吞异常并记录：BFF 侧接口为 void，对空/异常响应免疫；失败原因在此处留痕便于排查
            log.error("ES user location operation failed, esServerUrl={}", esServerUrl, e);
        }
    }

    @Override
    @GetMapping("/queryByUserId")
    public UserLocationVo queryByUserId(@RequestParam("userId") Long userId) {
        String url = this.esServerUrl + "api/es-user-location/queryByUserId?userId=" + userId;
        ResponseEntity<String> responseEntity;
        try {
            responseEntity = this.restTemplate.getForEntity(url, String.class);
        } catch (RestClientException e) {
            // 用户未上报位置时 ES 返回 404，RestTemplate 默认对 4xx 抛异常；属正常业务场景，返回 null 由上游处理
            log.warn("ES queryByUserId failed (user may have no location), userId={}, error={}", userId, e.getMessage());
            return null;
        }
        if (responseEntity.getStatusCode().value() != 200) {
            return null;
        }

        try {
            String body = responseEntity.getBody();
            JsonNode jsonNode = MAPPER.readTree(body);

            UserLocationVo userLocationVo = new UserLocationVo();
            // path() 安全访问：字段缺失返回 MissingNode 而非 null，避免链式 NPE
            JsonNode location = jsonNode.path("location");
            if (location.isMissingNode() || !location.has("lat") || !location.has("lon")) {
                log.warn("ES location field missing, userId={}", userId);
                return null;
            }
            userLocationVo.setLatitude(location.path("lat").asDouble());
            userLocationVo.setLongitude(location.path("lon").asDouble());
            userLocationVo.setUserId(userId);
            userLocationVo.setAddress(jsonNode.path("address").asText(""));
            userLocationVo.setId(userId.toString());
            userLocationVo.setCreated(jsonNode.path("created").asLong(0));
            userLocationVo.setUpdated(jsonNode.path("updated").asLong(0));
            userLocationVo.setLastUpdated(jsonNode.path("lastUpdated").asLong(0));

            return userLocationVo;

        } catch (Exception e) {
            log.error("ES user location operation failed, esServerUrl={}", esServerUrl, e);
        }

        return null;
    }

    @Override
    @GetMapping("/queryUserFromLocation")
    public List<UserLocationVo> queryUserFromLocation(@RequestParam("longitude") Double longitude,
                                                      @RequestParam("latitude") Double latitude,
                                                      @RequestParam("range") Integer range) {
        // ES Controller 端点为 GET /api/es-user-location/queryUserFromLocation?longitude=&latitude=&range=
        String url = this.esServerUrl + "api/es-user-location/queryUserFromLocation?longitude=" + longitude
                + "&latitude=" + latitude + "&range=" + range;
        try {
            ResponseEntity<String> responseEntity = this.restTemplate.getForEntity(url, String.class);
            if (responseEntity.getStatusCode().value() != 200) {
                return null;
            }

            String body = responseEntity.getBody();
            ArrayNode jsonNode = (ArrayNode) MAPPER.readTree(body);

            List<UserLocationVo> result = new ArrayList<>();

            for (JsonNode node : jsonNode) {
                // path() 安全访问：location 缺失的脏数据跳过，避免链式 NPE 中断整批结果
                JsonNode location = node.path("location");
                if (location.isMissingNode() || !location.has("lat") || !location.has("lon")) {
                    log.warn("ES location field missing in nearby list, userId={}", node.path("userId").asLong(0));
                    continue;
                }
                UserLocationVo userLocationVo = new UserLocationVo();
                userLocationVo.setLatitude(location.path("lat").asDouble());
                userLocationVo.setLongitude(location.path("lon").asDouble());
                userLocationVo.setUserId(node.path("userId").asLong(0));
                userLocationVo.setAddress(node.path("address").asText(""));
                userLocationVo.setId(String.valueOf(userLocationVo.getUserId()));
                userLocationVo.setCreated(node.path("created").asLong(0));
                userLocationVo.setUpdated(node.path("updated").asLong(0));
                userLocationVo.setLastUpdated(node.path("lastUpdated").asLong(0));
                result.add(userLocationVo);
            }

            return result;

        } catch (Exception e) {
            log.error("ES user location operation failed, esServerUrl={}", esServerUrl, e);
        }
        return null;
    }
}