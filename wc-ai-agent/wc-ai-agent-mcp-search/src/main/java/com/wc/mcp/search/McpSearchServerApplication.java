package com.wc.mcp.search;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import com.wc.mcp.search.tool.PexelsSearchTool;

/**
 * Pexels 媒体搜索 MCP 服务端 —— WebMVC/SSE 传输
 * <p>
 * 启动后默认监听 8081，SSE 端点路径由 spring-ai-starter-mcp-server-webmvc 自动暴露。
 */
@SpringBootApplication
public class McpSearchServerApplication
{

    public static void main(String[] args)
    {
        SpringApplication.run(McpSearchServerApplication.class, args);
    }

    @Bean
    public ToolCallbackProvider imageSearchTools(PexelsSearchTool imageSearchTool)
    {
        return MethodToolCallbackProvider.builder().toolObjects(imageSearchTool).build();
    }
}