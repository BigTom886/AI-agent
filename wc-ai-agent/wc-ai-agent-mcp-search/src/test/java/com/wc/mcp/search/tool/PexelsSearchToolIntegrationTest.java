package com.wc.mcp.search.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 真实 Pexels API 集成测试 —— 与 {@link PexelsSearchToolTest}（纯单元）互为补充。
 * <p>
 * ⚠️ 会真实调用 https://api.pexels.com，消耗 Pexels 免费配额（200/小时，20000/月）。
 * <ul>
 *   <li>依赖 {@code application.yml} 里的 {@code pexels.api-key} 有效</li>
 *   <li>依赖网络可达 {@code api.pexels.com}</li>
 * </ul>
 * <p>
 * 手动运行：
 * <pre>
 *   ./mvnw.cmd -pl wc-ai-agent-mcp-search test -Dtest=PexelsSearchToolIntegrationTest
 * </pre>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class PexelsSearchToolIntegrationTest {

    @Autowired
    private PexelsSearchTool tool;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("searchPhotos: 真实搜索 'computer'，断言至少 1 张照片")
    void searchPhotos_realApi() throws Exception {
        // act
        String json = tool.searchPhotos("computer", 5, null, null, null);

        // 真实结果打印到控制台，肉眼也能看
        System.out.println("\n========== Pexels searchPhotos ==========");
        System.out.println(prettyPrint(json));
        System.out.println("=========================================\n");

        // assert
        Assertions.assertNotNull(json, "返回值不应为 null");

        JsonNode root = mapper.readTree(json);
        Assertions.assertTrue(root.has("photos"), "JSON 应包含 photos 数组");
        Assertions.assertTrue(root.path("photos").isArray(), "photos 必须是数组");
        Assertions.assertTrue(root.path("photos").size() > 0,
                "Pexels 应返回至少 1 张照片，实际为 " + root.path("photos").size());
        Assertions.assertTrue(root.path("total_results").asInt(0) > 0,
                "total_results 应大于 0");
    }

    @Test
    @DisplayName("searchVideos: 真实搜索 'ocean waves'，断言至少 1 个视频")
    void searchVideos_realApi() throws Exception {
        // act —— PexelsSearchTool.searchVideos 是 7 个参数（无 page）
        String json = tool.searchVideos("ocean waves", 5, null, null, null, null, null);

        System.out.println("\n========== Pexels searchVideos ==========");
        System.out.println(prettyPrint(json));
        System.out.println("=========================================\n");

        // assert
        Assertions.assertNotNull(json, "返回值不应为 null");

        JsonNode root = mapper.readTree(json);
        Assertions.assertTrue(root.has("videos"), "JSON 应包含 videos 数组");
        Assertions.assertTrue(root.path("videos").isArray(), "videos 必须是数组");
        Assertions.assertTrue(root.path("videos").size() > 0,
                "Pexels 应返回至少 1 个视频，实际为 " + root.path("videos").size());
        Assertions.assertTrue(root.path("total_results").asInt(0) > 0,
                "total_results 应大于 0");
    }

    /** 工具方法：把 JSON 美化成多行，便于控制台阅读 */
    private String prettyPrint(String json) throws Exception {
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(mapper.readTree(json));
    }
}