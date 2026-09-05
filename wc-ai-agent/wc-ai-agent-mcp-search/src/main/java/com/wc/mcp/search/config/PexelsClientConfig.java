package com.wc.mcp.search.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Pexels HTTP 客户端配置
 * <p>
 * Pexels REST API 要求每个请求带上 {@code Authorization} 头，值为 API Key。
 */
@Configuration
public class PexelsClientConfig {

    @Bean
    public WebClient pexelsWebClient(
            @Value("${pexels.api-key:}") String apiKey,
            @Value("${pexels.base-url}") String baseUrl) {

        return WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, apiKey)
                .defaultHeader(HttpHeaders.USER_AGENT, "wc-ai-agent-mcp-search/1.0")
                .build();
    }
}