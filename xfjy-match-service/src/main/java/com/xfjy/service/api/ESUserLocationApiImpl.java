package com.xfjy.service.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
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
    @Value("${es.server.url:http://127.0.0.1:18088/es/}")
    private String esServerUrl;

    @Override
    @PostMapping("/updateUserLocation")
    public String updateUserLocation(@RequestParam("userId") Long userId,
                                     @RequestParam("longitude") Double longitude,
                                     @RequestParam("latitude") Double latitude,
                                     @RequestParam("address") String address) {
        try {
            String url = this.esServerUrl + "user/location";
            Map<String, Object> param = new HashMap<>();
            param.put("userId", userId);
            param.put("longitude", longitude);
            param.put("latitude", latitude);
            param.put("address", address);

            String requestBody = MAPPER.writeValueAsString(param);

            HttpHeaders httpHeaders = new HttpHeaders();
            httpHeaders.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> httpEntity = new HttpEntity<>(requestBody, httpHeaders);

            ResponseEntity<Void> responseEntity = this.restTemplate.postForEntity(url, httpEntity, Void.class);
            if (responseEntity.getStatusCode().value() == 200) {
                return userId.toString();
            }
        } catch (Exception e) {
            log.error("ES user location operation failed, esServerUrl={}", esServerUrl, e);
        }

        return null;
    }

    @Override
    @GetMapping("/queryByUserId")
    public UserLocationVo queryByUserId(@RequestParam("userId") Long userId) {
        String url = this.esServerUrl + "user/location/" + userId;
        ResponseEntity<String> responseEntity = this.restTemplate.getForEntity(url, String.class);
        if (responseEntity.getStatusCode().value() != 200) {
            return null;
        }

        try {
            String body = responseEntity.getBody();
            JsonNode jsonNode = MAPPER.readTree(body);

            UserLocationVo userLocationVo = new UserLocationVo();
            userLocationVo.setLatitude(jsonNode.get("location").get("lat").asDouble());
            userLocationVo.setLongitude(jsonNode.get("location").get("lon").asDouble());
            userLocationVo.setUserId(userId);
            userLocationVo.setAddress(jsonNode.get("address").asText());
            userLocationVo.setId(userId.toString());
            userLocationVo.setCreated(jsonNode.get("created").asLong());
            userLocationVo.setUpdated(jsonNode.get("updated").asLong());
            userLocationVo.setLastUpdated(jsonNode.get("lastUpdated").asLong());

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
        String url = this.esServerUrl + "user/location/list";
        Map<String, Object> param = new HashMap<>();
        param.put("longitude", longitude);
        param.put("latitude", latitude);
        param.put("distance", range);
        try {

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> httpEntity = new HttpEntity<>(MAPPER.writeValueAsString(param), headers);

            ResponseEntity<String> responseEntity = this.restTemplate.postForEntity(url, httpEntity, String.class);
            if (responseEntity.getStatusCode().value() != 200) {
                return null;
            }

            String body = responseEntity.getBody();
            ArrayNode jsonNode = (ArrayNode) MAPPER.readTree(body);

            List<UserLocationVo> result = new ArrayList<>();

            for (JsonNode node : jsonNode) {
                UserLocationVo userLocationVo = new UserLocationVo();
                userLocationVo.setLatitude(node.get("location").get("lat").asDouble());
                userLocationVo.setLongitude(node.get("location").get("lon").asDouble());
                userLocationVo.setUserId(node.get("userId").asLong());
                userLocationVo.setAddress(node.get("address").asText());
                userLocationVo.setId(userLocationVo.getUserId().toString());
                userLocationVo.setCreated(node.get("created").asLong());
                userLocationVo.setUpdated(node.get("updated").asLong());
                userLocationVo.setLastUpdated(node.get("lastUpdated").asLong());
                result.add(userLocationVo);
            }

            return result;

        } catch (Exception e) {
            log.error("ES user location operation failed, esServerUrl={}", esServerUrl, e);
        }
        return null;
    }
}