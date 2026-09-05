package com.wc.mcp.search.tool;

import com.wc.mcp.search.client.PexelsClient;
import com.wc.mcp.search.model.PexelsPhoto;
import com.wc.mcp.search.model.PexelsVideo;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PexelsSearchTool} 纯单元测试。
 * <p>
 * 不启 Spring、不连网络、不消耗 Pexels API 配额 —— 毫秒级跑完。
 * <p>
 * Page / Photo / Video 都是 record，只能通过规范构造器构造。
 */
class PexelsSearchToolTest
{

    private PexelsClient pexelsClient;
    private PexelsSearchTool tool;

    @BeforeEach
    void setUp()
    {
        pexelsClient = mock(PexelsClient.class);
        tool = new PexelsSearchTool(pexelsClient);
    }

    @Test
    void searchPhotos_returnsSerializedJson()
    {
        // arrange —— record 用规范构造器
        PexelsPhoto.Page stub = new PexelsPhoto.Page(1, // page
                10, // per_page
                List.of(), // photos
                1, // total_results
                null // next_page
        );
        when(pexelsClient.searchPhotos(eq("computer"), any(), any(), any(), any(), any())).thenReturn(Mono.just(stub));

        // act —— PexelsSearchTool.searchPhotos 是 5 个参数（无 page），page 在内部传给 client
        String json = tool.searchPhotos("computer", 10, null, null, null);
        System.out.println("searchPhotos 返回 JSON: " + json);

        // assert
        Assertions.assertNotNull(json);
        Assertions.assertTrue(json.contains("\"page\":1"), "JSON 应包含 page 字段");
        Assertions.assertTrue(json.contains("\"per_page\":10"), "JSON 应包含 per_page 字段");
        Assertions.assertTrue(json.contains("\"total_results\":1"), "JSON 应包含 total_results 字段");

        verify(pexelsClient).searchPhotos("computer", 10, null, null, null, null);
    }

    @Test
    void searchVideos_returnsSerializedJson()
    {
        // arrange
        PexelsVideo.Page stub = new PexelsVideo.Page(1, // page
                15, // per_page
                List.of(), // videos
                0, // total_results
                null, // url
                null // next_page
        );
        when(pexelsClient.searchVideos(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Mono.just(stub));

        // act —— PexelsSearchTool.searchVideos 是 7 个参数（无 page）
        String json = tool.searchVideos("ocean", 15, null, null, null, null, null);

        // assert
        Assertions.assertNotNull(json);
        Assertions.assertTrue(json.contains("\"page\":1"), "JSON 应包含 page 字段");
        Assertions.assertTrue(json.contains("\"per_page\":15"), "JSON 应包含 per_page 字段");

        verify(pexelsClient).searchVideos("ocean", 15, null, null, null, null, null, null);
    }

    @Test
    void searchPhotos_whenApiThrows_returnsErrorJson()
    {
        // arrange
        when(pexelsClient.searchPhotos(any(), any(), any(), any(), any(), any()))
                .thenReturn(Mono.error(new RuntimeException("pexels down")));

        // act
        String json = tool.searchPhotos("computer", 10, null, null, null);

        // assert
        Assertions.assertNotNull(json);
        Assertions.assertTrue(json.startsWith("{\"error\":"), "异常时应返回 {\"error\":...}");
        Assertions.assertTrue(json.contains("pexels down"), "异常信息应被序列化进 JSON");
    }
}