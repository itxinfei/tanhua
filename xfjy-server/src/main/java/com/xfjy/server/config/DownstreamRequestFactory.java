package com.xfjy.server.config;

import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.time.Duration;

/**
 * BFF 调用下游域服务的统一 HTTP 请求工厂。
 *
 * <p>口径：连接超时 2s、读取超时 5s。
 * <ul>
 *   <li>连接超时取 2s：内网 bridge 网络中可达实例建连应在毫秒级完成，
 *       2s 足够容忍偶发抖动，又能快速判定"实例不可达"，避免拖住调用线程。</li>
 *   <li>读取超时取 5s：域服务存在慢查询（推荐池召回、列表分页），
 *       过短会把正常请求判为超时，故与既有 {@code RestTemplate} 口径保持一致。</li>
 * </ul>
 *
 * <p>统一出口的意义：{@code RestTemplate} 与 {@code RestClient}（HTTP Interface 代理）
 * 是 BFF 的两条下游调用通道，超时口径必须一致，否则同一下游故障在两条通道上的
 * 表现（耗时、异常类型、线程占用）会不一致，难以定位。
 *
 * <p>实现上复用 {@link SimpleClientHttpRequestFactory}，不引入 HttpClient 5 / Jetty
 * 等额外连接池依赖（与既有配置保持同一实现，避免连接池参数成为新的调优点）。
 */
public final class DownstreamRequestFactory {

    /** 连接超时（毫秒） */
    public static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);

    /** 读取超时（毫秒） */
    public static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    private DownstreamRequestFactory() {
    }

    /**
     * 新建一个带超时设置的请求工厂。
     *
     * <p>每次调用返回新实例：{@link SimpleClientHttpRequestFactory} 基于
     * {@code HttpURLConnection}，本身无可变共享状态，但独立实例便于将来按服务
     * 粒度差异化超时，而不影响其他通道。
     */
    public static ClientHttpRequestFactory create() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);
        return factory;
    }
}
