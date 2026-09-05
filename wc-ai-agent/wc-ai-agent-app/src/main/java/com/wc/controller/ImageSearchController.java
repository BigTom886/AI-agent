package com.wc.controller;

import com.wc.app.LoveApp;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * MCP 客户端入口 —— 把远端 Pexels 搜索工具的能力通过 HTTP 暴露给前端。
 * <p>
 * 实际工具执行在 wc-ai-agent-mcp-search 服务端（8081），
 * 这里只负责：接收请求 → 调用 ChatClient.doChatWithMcp → 让 LLM 决定是否调用工具。
 */
@RestController // 把当前类标记为控制器，把方法返回值直接序列化为 JSON（或字符串）返回给 HTTP 响应体，而不是去找视图页面跳转。
@RequestMapping("/mcp")
@RequiredArgsConstructor // 自动生成构造函数
@Validated // 用来做Java 对象参数校验
@Slf4j
@Tag(name = "MCP 图片搜索", description = "通过 MCP 客户端调用远端 Pexels 搜索服务")
public class ImageSearchController {

    private final LoveApp loveApp;

    /**
     * 走 LLM 自然语言路由 → MCP 工具调用
     * <p>
     * 用户问"帮我搜几张山的图片"，模型自动判断是否调用 searchPhotos 工具，
     * 并把工具返回的 JSON 整理成自然语言回复。
     *
     * @param message 用户问题，必填
     * @param chatId  会话 ID，用于对话记忆隔离
     */
    @GetMapping("/chat")
    @Operation(summary = "自然语言调用 MCP 工具", description = "把用户问题交给 LLM，LLM 自行决定是否调用 searchPhotos / searchVideos")
    public ResponseEntity<?> chat(
            @Parameter(description = "用户自然语言问题，例如：帮我搜几张山的图片", required = true) @RequestParam @NotBlank(message = "message 不能为空") String message,
            @Parameter(description = "会话 ID，用于对话记忆隔离") @RequestParam(defaultValue = "default") String chatId) {

        try {
            String content = loveApp.doChatWithMcp(message, chatId);
            return ResponseEntity.ok(content);
        } catch (Exception e) {
            // MCP 服务未启动 / 连接断开 / 工具调用失败 都会进这里
            log.error("MCP chat 调用失败: message={}, chatId={}", message, chatId, e);
            return ResponseEntity.status(503)
                    .body("MCP 服务暂不可用: " + e.getClass().getSimpleName()
                            + ": " + (e.getMessage() == null ? "unknown" : e.getMessage()));
        }
    }
}
