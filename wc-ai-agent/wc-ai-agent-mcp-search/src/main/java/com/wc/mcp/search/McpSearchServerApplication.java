package com.wc.mcp.search;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import com.wc.mcp.search.tool.PexelsSearchTool;

/**
 * Pexels 媒体搜索 MCP 服务端 —— STDIO 传输
 * <p>
 * 不作为独立 Web 服务启动，而是由 MCP 客户端（wc-ai-agent-app）通过
 * "java -jar" 拉起为子进程，双方经标准输入/输出交换 JSON-RPC 报文。
 * 注意：stdout 是协议通道，所有日志必须输出到 stderr（见 logback-spring.xml）。
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