package com.xfjy.server.service;

import com.xfjy.server.exception.BusinessException;
import com.xfjy.server.vo.LocationVo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 定位辅助服务：经纬度逆地理 + IP 兜底定位
 *
 * <p>背景：H5 端在非 HTTPS 环境下浏览器直接禁用 Geolocation，且 uni.chooseLocation 的
 * 地图 Key 是编译期写死在 manifest 里的，运营无法在后台调整。因此把「把坐标翻译成地址」
 * 和「拿不到坐标时按 IP 兜底到城市」这两件事收到服务端，Key 走 tb_sys_config，
 * 后台「系统配置 → 地图服务」可维护，改完 30 秒内生效。</p>
 *
 * <p>Key 分级：{@code map.amap.web-key}（Web 服务 API，只在服务端使用，绝不下发前端）；
 * {@code map.amap.js-key} / {@code map.amap.security-js-code} 是前端 JSAPI 用，可下发。</p>
 *
 * <p>降级：Key 未配置时抛 400 并带明确文案，前端展示「地图服务未配置」而不是「检查网络」。</p>
 */
@Service
public class LocationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(LocationService.class);

    /** 高德逆地理编码（Web 服务 API） */
    private static final String AMAP_REGEO_URL = "https://restapi.amap.com/v3/geocode/regeo";

    /** 高德 IP 定位（Web 服务 API） */
    private static final String AMAP_IP_URL = "https://restapi.amap.com/v3/ip";

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

    /** JSON 字段提取：高德返回结构固定，只取字符串字段，避免引入额外解析依赖 */
    private static final Pattern STRING_FIELD = Pattern.compile("\"([A-Za-z_]+)\"\\s*:\\s*\"([^\"]*)\"");

    @Autowired
    private SysConfigService sysConfigService;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /**
     * 经纬度逆地理：坐标 → 可读地址
     *
     * @param longitude 经度（gcj02）
     * @param latitude  纬度（gcj02）
     */
    public LocationVo reverseGeocode(Double longitude, Double latitude) {
        if (longitude == null || latitude == null) {
            throw new BusinessException("缺少经纬度参数");
        }
        if (Math.abs(longitude) > 180D || Math.abs(latitude) > 90D) {
            throw new BusinessException("经纬度超出有效范围");
        }

        String key = requireWebKey();
        String url = AMAP_REGEO_URL
                + "?key=" + encode(key)
                + "&location=" + trimCoordinate(longitude) + "," + trimCoordinate(latitude)
                + "&extensions=base&radius=200&output=json";

        String body = httpGet(url, "逆地理编码");
        if (!"1".equals(field(body, "status"))) {
            LOGGER.warn("逆地理编码失败: info={}, infocode={}, 坐标={},{}",
                    field(body, "info"), field(body, "infocode"), longitude, latitude);
            throw new BusinessException("地址解析失败，请稍后重试");
        }

        LocationVo vo = new LocationVo();
        vo.setLongitude(longitude);
        vo.setLatitude(latitude);
        vo.setSource("gps");

        String formattedAddress = field(body, "formatted_address");
        String province = field(body, "province");
        String city = field(body, "city");
        String district = field(body, "district");
        String adcode = field(body, "adcode");

        vo.setAddress(formattedAddress);
        vo.setProvince(province);
        vo.setCity(city);
        vo.setDistrict(district);
        vo.setAdcode(adcode);
        vo.setName(shortName(province, city, district, formattedAddress));
        return vo;
    }

    /**
     * IP 兜底定位：设备定位不可用（HTTP 非安全上下文、用户拒绝授权）时，按来源 IP 落到城市级
     *
     * <p>精度只到城市，用于「附近的人」兜底展示与默认城市选择；私网/回环地址不传给高德，
     * 交由高德按服务端出口 IP 判断（生产多机房时可能落到机房所在城市，属预期降级）。</p>
     *
     * @param clientIp 客户端 IP（可能为私网地址）
     */
    public LocationVo ipLocation(String clientIp) {
        String key = requireWebKey();
        String url = AMAP_IP_URL + "?key=" + encode(key) + "&output=json";
        if (isPublicIp(clientIp)) {
            url += "&ip=" + encode(clientIp);
        }

        String body = httpGet(url, "IP定位");
        if (!"1".equals(field(body, "status"))) {
            LOGGER.warn("IP定位失败: info={}, infocode={}, ip={}",
                    field(body, "info"), field(body, "infocode"), clientIp);
            throw new BusinessException("无法根据网络位置判断城市，请手动选择城市");
        }

        String province = field(body, "province");
        String city = field(body, "city");
        if (province.isEmpty() && city.isEmpty()) {
            throw new BusinessException("无法根据网络位置判断城市，请手动选择城市");
        }

        LocationVo vo = new LocationVo();
        vo.setSource("ip");
        vo.setProvince(province);
        vo.setCity(city.isEmpty() ? province : city);
        vo.setDistrict("");
        vo.setAdcode(field(body, "adcode"));
        vo.setName(vo.getCity());

        // rectangle 为城市矩形边界「lng1,lat1;lng2,lat2」，取中心点作为近似坐标
        String rectangle = field(body, "rectangle");
        double[] center = rectangleCenter(rectangle);
        if (center != null) {
            vo.setLongitude(center[0]);
            vo.setLatitude(center[1]);
        }
        vo.setAddress(vo.getName());
        return vo;
    }

    /** 读取服务端 Web 服务 Key（未配置时给出可执行的指引） */
    private String requireWebKey() {
        String key = this.sysConfigService.getConfig("map.amap.web-key", "");
        if (key == null || key.trim().isEmpty()) {
            throw new BusinessException("地图服务未配置：请在后台「系统配置 → 地图服务」填写高德 Web 服务 Key");
        }
        return key.trim();
    }

    /** 发起 GET 并返回响应体；网络/状态码异常统一转业务异常，不向前端暴露内部细节 */
    private String httpGet(String url, String scene) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = this.httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                LOGGER.warn("{}接口HTTP异常: status={}", scene, response.statusCode());
                throw new BusinessException(scene + "服务暂时不可用，请稍后重试");
            }
            return response.body() == null ? "" : response.body();
        } catch (BusinessException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.warn("{}请求被中断", scene, e);
            throw new BusinessException(scene + "请求被中断，请重试");
        } catch (Exception e) {
            LOGGER.warn("{}请求失败: err={}", scene, e.getMessage());
            throw new BusinessException(scene + "服务连接失败，请稍后重试");
        }
    }

    /** 取 JSON 中第一个匹配的字符串字段值，缺失返回空串 */
    private static String field(String json, String name) {
        if (json == null || json.isEmpty()) {
            return "";
        }
        Matcher matcher = STRING_FIELD.matcher(json);
        while (matcher.find()) {
            if (name.equals(matcher.group(1))) {
                return matcher.group(2);
            }
        }
        return "";
    }

    /** 展示名：优先「市+区」，其次「省+市」，最后退回完整地址 */
    private static String shortName(String province, String city, String district, String formattedAddress) {
        StringBuilder builder = new StringBuilder();
        if (!city.isEmpty()) {
            builder.append(city);
        } else if (!province.isEmpty()) {
            builder.append(province);
        }
        if (!district.isEmpty()) {
            builder.append(district);
        }
        if (builder.length() == 0) {
            return formattedAddress;
        }
        return builder.toString();
    }

    /** 坐标保留 6 位小数，避免科学计数法传给高德 */
    private static String trimCoordinate(Double value) {
        return String.format(java.util.Locale.ROOT, "%.6f", value);
    }

    /** 解析「lng,lat;lng,lat」矩形，返回中心点；格式不符返回 null */
    private static double[] rectangleCenter(String rectangle) {
        if (rectangle == null || !rectangle.contains(";")) {
            return null;
        }
        String[] points = rectangle.split(";");
        if (points.length != 2) {
            return null;
        }
        double[] first = parseLngLat(points[0]);
        double[] second = parseLngLat(points[1]);
        if (first == null || second == null) {
            return null;
        }
        return new double[]{(first[0] + second[0]) / 2D, (first[1] + second[1]) / 2D};
    }

    private static double[] parseLngLat(String point) {
        String[] parts = point.split(",");
        if (parts.length != 2) {
            return null;
        }
        try {
            return new double[]{Double.parseDouble(parts[0].trim()), Double.parseDouble(parts[1].trim())};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 公网 IP 才可交给高德做 IP 定位；私网/回环一律交给高德按服务端出口 IP 判断 */
    private static boolean isPublicIp(String ip) {
        if (ip == null || ip.trim().isEmpty()) {
            return false;
        }
        String value = ip.trim();
        if ("127.0.0.1".equals(value) || "0:0:0:0:0:0:0:1".equals(value) || "::1".equals(value)) {
            return false;
        }
        if (value.startsWith("10.")
                || value.startsWith("192.168.")
                || value.startsWith("169.254.")) {
            return false;
        }
        if (value.startsWith("172.")) {
            String[] parts = value.split("\\.");
            if (parts.length > 1) {
                try {
                    int second = Integer.parseInt(parts[1]);
                    return second < 16 || second > 31;
                } catch (NumberFormatException e) {
                    return false;
                }
            }
            return false;
        }
        return true;
    }

    /** URL 参数编码（Key 为字母数字，保留原样；其余按 UTF-8 编码） */
    private static String encode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
