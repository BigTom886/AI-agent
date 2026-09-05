package com.wc.mcp.search.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Pexels 视频搜索结果
 *
 * @see <a href="https://www.pexels.com/api/documentation/#videos-overview">Pexels API Docs</a>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PexelsVideo(
        Long id,
        Integer width,
        Integer height,
        Integer duration,
        String url,
        String image,
        User user,
        List<VideoFile> video_files,
        List<VideoPicture> video_pictures
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record User(
            Long id,
            String name,
            String url
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record VideoFile(
            Long id,
            String quality,
            String file_type,
            Integer width,
            Integer height,
            Double fps,
            String link
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record VideoPicture(
            Long id,
            String picture,
            Integer nr
    ) {}

    /** 列表结果 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Page(
            Integer page,
            Integer per_page,
            List<PexelsVideo> videos,
            Integer total_results,
            String url,
            String next_page
    ) {}
}