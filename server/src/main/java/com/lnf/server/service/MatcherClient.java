package com.lnf.server.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * 匹配引擎微服务（matcher/）HTTP 客户端。
 * 不可用时不抛异常、返回 null，由调用方降级（不阻塞发布主流程）。
 */
@Slf4j
@Component
public class MatcherClient {

    private final String baseUrl;
    private final RestTemplate restTemplate;

    public MatcherClient(@Value("${matcher.base-url}") String baseUrl) {
        this.baseUrl = baseUrl;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(10000);
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * 文本向量化：POST /embed/text。matcher 不可用时返回 null。
     */
    @SuppressWarnings("unchecked")
    public List<Double> embedText(String text) {
        try {
            Map<String, Object> response = restTemplate.postForObject(
                    baseUrl + "/embed/text", Map.of("text", text), Map.class);
            if (response == null || !(response.get("vector") instanceof List<?> vector)) {
                log.warn("matcher /embed/text 返回异常: {}", response);
                return null;
            }
            return (List<Double>) vector;
        } catch (Exception e) {
            log.warn("matcher 服务不可用（{}）：{}", baseUrl, e.getMessage());
            return null;
        }
    }
}
