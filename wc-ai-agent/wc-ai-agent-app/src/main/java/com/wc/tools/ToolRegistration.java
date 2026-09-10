package com.wc.tools;

import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class ToolRegistration {

    @Value("${search-api.api-key}")
    private String searchApiKey;

    /**
     * 汇总本地工具与 MCP 服务端提供的工具。
     *
     * @param toolCallbackProviders Spring AI 自动装配的 ToolCallbackProvider（如 MCP 客户端）。
     *                              用 ObjectProvider 接收，MCP 未启用时不会导致启动失败。
     */
    @Bean
    public ToolCallback[] allTools(ObjectProvider<ToolCallbackProvider> toolCallbackProviders) {
        FileOperationTool fileOperationTool = new FileOperationTool();
        WebSearchTool webSearchTool = new WebSearchTool(searchApiKey);
        WebScrapingTool webScrapingTool = new WebScrapingTool();
        ResourceDownloadTool resourceDownloadTool = new ResourceDownloadTool();
        TerminalOperationTool terminalOperationTool = new TerminalOperationTool();
        PDFGenerationTool pdfGenerationTool = new PDFGenerationTool();
        TerminateTool terminateTool = new TerminateTool();

        // 本地工具：由 @Tool 注解反射扫描生成
        ToolCallback[] localTools = ToolCallbacks.from(fileOperationTool, webSearchTool, webScrapingTool,
                resourceDownloadTool, terminalOperationTool, pdfGenerationTool, terminateTool);

        // MCP 工具：来自 wc-ai-agent-mcp-search（pexels 图片搜索）等远程服务端
        ToolCallback[] mcpTools = toolCallbackProviders.stream()
                .flatMap(provider -> Arrays.stream(provider.getToolCallbacks()))
                .toArray(ToolCallback[]::new);

        ToolCallback[] allTools = Stream.concat(Arrays.stream(localTools), Arrays.stream(mcpTools))
                .toArray(ToolCallback[]::new);

        log.info("已注册工具 {} 个（本地 {} 个，MCP {} 个）: {}",
                allTools.length, localTools.length, mcpTools.length,
                Arrays.stream(allTools)
                        .map(tool -> tool.getToolDefinition().name())
                        .collect(Collectors.joining(", ")));

        return allTools;
    }
}
