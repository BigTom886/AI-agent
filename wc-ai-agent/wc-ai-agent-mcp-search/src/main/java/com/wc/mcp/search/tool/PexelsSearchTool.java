package com.wc.mcp.search.tool;

import com.wc.mcp.search.client.PexelsClient;
import com.wc.mcp.search.model.PexelsPhoto;
import com.wc.mcp.search.model.PexelsVideo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 暴露给 MCP 客户端的工具方法
 * <p>
 * 每个 {@code @Tool} 方法会被 Spring AI 注册成一个可被 LLM 调用的工具。 返回值是结构化 JSON 字符串（Jackson
 * 自动序列化），便于客户端解析。
 */
@Component
public class PexelsSearchTool
{

    private static final Logger log = LoggerFactory.getLogger(PexelsSearchTool.class);

    private final PexelsClient pexelsClient;

    public PexelsSearchTool(PexelsClient pexelsClient)
    {
        this.pexelsClient = pexelsClient;
    }

    /**
     * 按关键词在 Pexels 上搜索免费高清图片。
     *
     * @param query 搜索关键词，必填，例如 "mountain"、"sunset beach"
     * @param perPage 每页返回数量，可选，范围 1-80，默认 10
     * @param orientation 方向，可选，landscape / portrait / square
     * @param size 最小尺寸，可选，large / medium / small
     * @param color 主色调，可选，例如 red、blue、yellow 等
     * @return JSON 字符串，包含 total_results、page、photos[] 等字段； 每张图片含
     * id、photographer、url、各尺寸 src 等
     */
    @Tool(description = "Search free stock photos on Pexels by keyword. "
            + "Returns a JSON page of photos with id, photographer, page URL, "
            + "and image URLs in multiple sizes (original, large2x, large, medium, small, portrait, landscape, tiny).")
    public String searchPhotos(@ToolParam(description = "Search keyword, e.g. 'mountain', 'sunset beach'") String query,
            @ToolParam(description = "Results per page (1-80). Default 10.", required = false) Integer perPage,
            @ToolParam(description = "Photo orientation: landscape / portrait / square.", required = false) String orientation,
            @ToolParam(description = "Minimum size: large / medium / small.", required = false) String size,
            @ToolParam(description = "Dominant color name, e.g. red, blue, yellow.", required = false) String color)
    {

        log.info("MCP tool searchPhotos: query={}, perPage={}, orientation={}, size={}, color={}", query, perPage,
                orientation, size, color);

        try
        {
            PexelsPhoto.Page page = pexelsClient.searchPhotos(query, perPage, null, orientation, size, color).block();

            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(page);
        } catch (Exception e)
        {
            log.error("searchPhotos failed", e);
            return "{\"error\":\"" + e.getMessage().replace("\"", "'") + "\"}";
        }
    }

    /**
     * 按关键词在 Pexels 上搜索免费视频。
     */
    @Tool(description = "Search free stock videos on Pexels by keyword. "
            + "Returns a JSON page of videos with id, duration, user, image, "
            + "video_files (with quality, fps, link) and preview pictures.")
    public String searchVideos(
            @ToolParam(description = "Search keyword, e.g. 'ocean waves', 'city skyline'") String query,
            @ToolParam(description = "Results per page (1-80). Default 15.", required = false) Integer perPage,
            @ToolParam(description = "Video orientation: landscape / portrait / square.", required = false) String orientation,
            @ToolParam(description = "Minimum video width in px.", required = false) Integer minWidth,
            @ToolParam(description = "Minimum video height in px.", required = false) Integer minHeight,
            @ToolParam(description = "Minimum duration in seconds.", required = false) Integer minDuration,
            @ToolParam(description = "Maximum duration in seconds.", required = false) Integer maxDuration)
    {

        log.info("MCP tool searchVideos: query={}, perPage={}, orientation={}", query, perPage, orientation);

        try
        {
            PexelsVideo.Page page = pexelsClient
                    .searchVideos(query, perPage, null, orientation, minWidth, minHeight, minDuration, maxDuration)
                    .block();

            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(page);
        } catch (Exception e)
        {
            log.error("searchVideos failed", e);
            return "{\"error\":\"" + e.getMessage().replace("\"", "'") + "\"}";
        }
    }
}