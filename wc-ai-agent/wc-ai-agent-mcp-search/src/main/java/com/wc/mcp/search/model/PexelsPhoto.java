package com.wc.mcp.search.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Pexels 图片搜索结果
 * <p>
 * 仅保留 MCP 工具对外暴露需要用到的子集。
 *
 * @see <a href="https://www.pexels.com/api/documentation/#photos-overview">Pexels API Docs</a>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PexelsPhoto(
        Long id,
        Integer width,
        Integer height,
        String url,
        String photographer,
        String alt_text,
        Src src,
        String avg_color
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Src(
            String original,
            String large2x,
            String large,
            String medium,
            String small,
            String portrait,
            String landscape,
            String tiny
    ) {}

    /** 列表结果 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Page(
            Integer page,
            Integer per_page,
            List<PexelsPhoto> photos,
            Integer total_results,
            String next_page
    ) {}
}