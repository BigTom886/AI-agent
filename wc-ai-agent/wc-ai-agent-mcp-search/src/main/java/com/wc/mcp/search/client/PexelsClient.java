package com.wc.mcp.search.client;

import com.wc.mcp.search.model.PexelsPhoto;
import com.wc.mcp.search.model.PexelsVideo;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Pexels API 访问封装（响应式）
 * <p>
 * - {@code /v1/search} 搜图片
 * - {@code /v1/videos/search} 搜视频
 */
@Component
public class PexelsClient {

    private final WebClient webClient;

    public PexelsClient(WebClient pexelsWebClient) {
        this.webClient = pexelsWebClient;
    }

    /**
     * 搜图片
     *
     * @param query      必填关键词
     * @param perPage    每页条数（1-80），默认 15
     * @param page       页码，默认 1
     * @param orientation landscape/portrait/square，可选
     * @param size       large/medium/small，可选
     * @param color      颜色（red/orange/yellow/green/...），可选
     */
    public Mono<PexelsPhoto.Page> searchPhotos(
            String query,
            Integer perPage,
            Integer page,
            String orientation,
            String size,
            String color) {

        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v1/search")
                        .queryParam("query", query)
                        .queryParamIfPresent("per_page", java.util.Optional.ofNullable(perPage))
                        .queryParamIfPresent("page", java.util.Optional.ofNullable(page))
                        .queryParamIfPresent("orientation", java.util.Optional.ofNullable(orientation))
                        .queryParamIfPresent("size", java.util.Optional.ofNullable(size))
                        .queryParamIfPresent("color", java.util.Optional.ofNullable(color))
                        .build())
                .retrieve()
                .bodyToMono(PexelsPhoto.Page.class);
    }

    /**
     * 搜视频
     */
    public Mono<PexelsVideo.Page> searchVideos(
            String query,
            Integer perPage,
            Integer page,
            String orientation,
            Integer minWidth,
            Integer minHeight,
            Integer minDuration,
            Integer maxDuration) {

        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v1/videos/search")
                        .queryParam("query", query)
                        .queryParamIfPresent("per_page", java.util.Optional.ofNullable(perPage))
                        .queryParamIfPresent("page", java.util.Optional.ofNullable(page))
                        .queryParamIfPresent("orientation", java.util.Optional.ofNullable(orientation))
                        .queryParamIfPresent("min_width", java.util.Optional.ofNullable(minWidth))
                        .queryParamIfPresent("min_height", java.util.Optional.ofNullable(minHeight))
                        .queryParamIfPresent("min_duration", java.util.Optional.ofNullable(minDuration))
                        .queryParamIfPresent("max_duration", java.util.Optional.ofNullable(maxDuration))
                        .build())
                .retrieve()
                .bodyToMono(PexelsVideo.Page.class);
    }
}