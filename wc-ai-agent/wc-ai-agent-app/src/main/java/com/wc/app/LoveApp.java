package com.wc.app;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.wc.demo.invoke.LoggingAdvisor;
import com.wc.userprofile.UserProfileAdvisor;
import com.wc.userprofile.UserProfileTools;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

/**
 * 恋爱顾问应用 —— 基于 Spring AI ChatClient 与大模型交互
 *
 * ChatClient 核心概念： ChatClient 是 Spring AI 提供的流畅 API（Fluent
 * API）客户端，用于以声明式、链式调用的方式 向 AI 模型发送提示词并获取响应，设计理念类似于 Spring WebFlux 中的 WebClient。
 *
 * 核心组件： - ChatModel —— 底层模型适配器，本项目通过 OpenAI 兼容协议接入（服务商由 AI_BASE_URL 决定） - Advisors
 * —— 拦截器链，类似 Servlet Filter，可在请求前后做增强（如记忆、日志、重试等） - ChatMemory ——
 * 对话记忆，维护多轮对话上下文 - ToolCallback —— 工具，模型可以自己决定调用
 *
 * ChatClient 关键 API 速览： - ChatClient.builder(chatModel) → 创建构建器，绑定底层 ChatModel
 * - .defaultSystem("...") → 设置默认系统提示词 - .defaultAdvisors(...) → 注册默认
 * Advisor（拦截器） - .prompt().user("...") → 设置用户消息 - .prompt().system("...") →
 * 设置本次系统消息（覆盖默认） - .call() → 同步调用，返回 ChatResponse - .stream() → 流式返回
 * Flux<String> - .content() → 从响应中提取纯文本内容
 */
@Component
@Slf4j
public class LoveApp {

        /** ChatClient 实例，用于与 AI 模型交互 */
        private final ChatClient chatClient;

        /**
         * 报告专用 ChatClient —— 与主对话的 ChatClient 唯一的差别：
         * 不挂 MessageChatMemoryAdvisor。
         *
         * 为什么要分开？报告是一次性产物，不应该出现在聊天记录里。
         * 复用主 ChatClient 的话，Advisor 会把「生成报告」这条请求和报告正文
         * 当成一轮正常对话写进 SPRING_AI_CHAT_MEMORY，前端刷新后会多出一条
         * 莫名其妙的 AI 消息。
         *
         * 对话上下文改为手工拼进 user 消息（见 buildReportPrompt），效果一样，
         * 但不会污染记忆表。
         */
        private final ChatClient reportChatClient;

        /**
         * 对话记忆 —— 完全由 Spring AI 自动装配提供：
         * MessageWindowChatMemory（默认窗口 20 条）+ JdbcChatMemoryRepository（PostgreSQL 持久化），
         * 本项目不写任何记忆相关代码，只在调用时传 conversationId。
         */
        private final ChatMemory chatMemory;

        /**
         * 用户画像工具的回调 —— 让模型自己决定「什么时候该记住用户的事」。
         *
         * 单独一组，不混进 allTools：allTools 也会喂给 YuManus 智能体，
         * 而 YuManus 自己执行工具调用时没有 toolContext，
         * 带 ToolContext 参数的工具在那里会因为拿不到上下文直接报错。
         */
        private final ToolCallback[] profileToolCallbacks;

        /**
         * 系统提示词 —— 定义 AI 的角色和行为规范 通过 ChatClient.builder().defaultSystem() 设置，每次对话都会自动携带
         * 从 classpath:prompts/love-master-system.md 加载，改提示词无需重新编译
         */
        private static final String SYSTEM_PROMPT = loadPrompt("prompts/love-master-system.md");

        /**
         * 恋爱报告专用提示词 —— 与主对话人设分离，避免生成报告时污染对话风格
         */
        private static final String REPORT_SYSTEM_PROMPT = loadPrompt("prompts/love-report-system.md");

        /**
         * 从 classpath 读取提示词文件（UTF-8）
         *
         * @param path 相对 resources 的路径，如 prompts/love-master-system.md
         * @return 提示词全文
         */
        private static String loadPrompt(String path) {
                try {
                        return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
                } catch (IOException e) {
                        throw new IllegalStateException("加载提示词失败：" + path, e);
                }
        }

        /**
         * 构造函数 —— 注入 ChatModel 与 ChatMemory 并构建 ChatClient
         *
         * Spring AI 自动配置会根据 application.yml 中的模型配置注入 ChatModel Bean， 然后我们手动构建
         * ChatClient 来添加 Advisor 链（如对话记忆增强器）。
         *
         * 为什么不直接注入 ChatClient？因为自动配置的 ChatClient 不带 Advisor， 我们需要通过 builder 自定义添加
         * MessageChatMemoryAdvisor。
         *
         * @param chatModel Spring AI 自动注入的底层模型适配器（OpenAI 兼容协议）
         * @param chatMemory 对话记忆实现（持久化到 PostgreSQL）
         * @param userProfileAdvisor 用户画像 Advisor（把跨会话画像注入系统提示词）
         * @param userProfileTools 用户画像工具（@Tool 注解）
         */
        public LoveApp(ChatModel chatModel, ChatMemory chatMemory,
                        UserProfileAdvisor userProfileAdvisor, UserProfileTools userProfileTools) {

                this.chatMemory = chatMemory;
                this.profileToolCallbacks = ToolCallbacks.from(userProfileTools);

                this.chatClient = ChatClient.builder(chatModel)
                                // 设置默认系统提示词，定义 AI 的角色和行为
                                .defaultSystem(SYSTEM_PROMPT)
                                // 注册 Advisor 链：每次请求自动携带历史消息 + 自动注入用户画像
                                .defaultAdvisors(
                                                // 对话记忆 Advisor：每次请求自动携带历史消息，响应后自动保存新消息
                                                MessageChatMemoryAdvisor.builder(chatMemory).build(),
                                                // 用户画像 Advisor：把跨会话画像拼到系统提示词里
                                                userProfileAdvisor)
                                .build();

                // 报告专用：只挂画像 Advisor，不挂记忆 Advisor（原因见字段注释）
                this.reportChatClient = ChatClient.builder(chatModel)
                                .defaultSystem(REPORT_SYSTEM_PROMPT)
                                .defaultAdvisors(userProfileAdvisor)
                                .build();
        }

        /**
         * 执行对话 —— 向 AI 模型发送用户输入并返回文本响应
         *
         * 调用链路解析： 1. prompt() → 创建 Prompt 构建器 2. .user(userInput) → 设置用户消息内容 3.
         * .advisors(a -> a.param(...)) → 运行时向 MessageChatMemoryAdvisor 传入会话 ID，
         * 让它按会话隔离记忆（不同 chatId 的对话互不干扰） 4. .call() → 同步调用 AI 模型（另有 .stream() 用于流式响应） 5.
         * .content() → 从 ChatResponse 中提取纯文本内容
         *
         * @param userInput 用户输入的文本
         * @param chatId    对话 ID（用于区分不同会话的记忆）
         * @param userId    用户 ID（用于跨会话画像），由 Controller 从登录 session 派生
         * @return AI 模型的文本响应
         */
        public String doChat(String userInput, String chatId, String userId) {
                return prepare(userInput, chatId, userId).call().content();
        }

        public Flux<String> doChatByStream(String message, String chatId, String userId) {
                return prepare(message, chatId, userId).stream().content();
        }

        /**
         * 生成恋爱报告
         *
         * 报告的输入 = 当前会话的对话记录 + 跨会话用户画像，
         * 输出 = 一个标题 + 3~5 条建议。
         *
         * 注意：这条链路走的是 reportChatClient（不带记忆 Advisor），
         * 所以生成报告不会往对话记录里写任何东西。
         *
         * @param userInput 用户本次想重点了解什么（可为空，为空则针对整段对话）
         * @param chatId    会话 ID，用来取这段对话的记录
         * @param userId    用户 ID（用于跨会话画像），由 Controller 从登录 session 派生
         * @return 报告
         */
        public LoveReport generateLoveReport(String userInput, String chatId, String userId) {
                // 对话上下文：手工从记忆里取，拼进 user 消息
                String transcript = buildTranscript(chatId);
                String prompt = buildReportPrompt(userInput, transcript);

                // 使用报告专用提示词，避免与主对话人设互相污染
                String reportText = this.reportChatClient.prompt()
                                .user(prompt)
                                .advisors(a -> a.param(UserProfileAdvisor.USER_ID, effectiveUserId(userId)))
                                .call().content();

                // 约定：首行为标题，其余每行一条建议
                List<String> lines = reportText.lines()
                                .map(String::trim)
                                .filter(line -> !line.isEmpty())
                                .toList();
                String title = lines.isEmpty() ? "你的恋爱报告" : lines.get(0);
                List<String> suggestions = lines.size() > 1 ? lines.subList(1, lines.size()) : List.of();

                log.info("生成恋爱报告: title={}, suggestions={}", title, suggestions.size());

                return new LoveReport(title, suggestions);
        }

        /**
         * 取出会话当前的记忆内容，拼成一段纯文本记录
         *
         * 取的是「模型当时看到的那份」：老消息已被压成摘要，最近几条保留原文，
         * 所以这里的 transcript 和模型在聊天时看到的上下文是一致的。
         *
         * @param chatId 会话 ID
         * @return 形如「用户：…\nAI：…」的文本；没有记录时返回空串
         */
        private String buildTranscript(String chatId) {
                List<Message> messages = chatMemory.get(chatId);
                if (messages == null || messages.isEmpty()) {
                        return "";
                }
                return messages.stream()
                                // 只保留对话本身：系统提示词和工具调用记录对报告没意义
                                .filter(m -> m.getMessageType() == MessageType.USER
                                                || m.getMessageType() == MessageType.ASSISTANT)
                                .map(m -> {
                                        String text = m.getText() == null ? "" : m.getText();
                                        return m.getMessageType() == MessageType.USER ? "用户：" + text : "AI：" + text;
                                })
                                .filter(line -> !line.endsWith("："))
                                .collect(Collectors.joining("\n"));
        }

        /**
         * 组装报告请求的用户消息
         *
         * @param userInput  用户本次想重点了解什么（可为空）
         * @param transcript 当前会话的对话记录（可为空）
         * @return 交给模型的用户消息
         */
        private String buildReportPrompt(String userInput, String transcript) {
                StringBuilder sb = new StringBuilder();
                if (userInput != null && !userInput.isBlank()) {
                        sb.append("用户的具体诉求：").append(userInput.trim()).append("\n\n");
                }
                if (!transcript.isEmpty()) {
                        sb.append("# 对话记录\n").append(transcript).append("\n\n");
                }
                sb.append("请根据以上内容，按要求输出一份简短的恋爱分析报告。");
                return sb.toString();
        }


        @Resource
        private ToolCallback[] allTools;

        public String doChatWithTools(String message, String chatId, String userId) {
                ChatResponse response = chatClient.prompt().user(message)
                                // 必须带上会话 ID：MessageChatMemoryAdvisor 靠它存取记忆，
                                // 缺省会抛 "conversationId cannot be null or empty"
                                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId)
                                                .param(UserProfileAdvisor.USER_ID, effectiveUserId(userId)))
                                // 已包装好的 ToolCallback[] 必须用 .toolCallbacks(),用 .tools() 会再次反射找 @Tool 导致失败
                                .toolCallbacks(allTools)
                                .toolCallbacks(profileToolCallbacks)
                                .toolContext(Map.of(UserProfileTools.USER_ID, effectiveUserId(userId)))
                                .call().chatResponse();
                String content = response.getResult().getOutput().getText();
                // log.info("content: {}", content);
                return content;
        }

        // prod 环境排除了 MCP 客户端自动装配，该 Bean 不存在，允许为 null；
        // doChatWithMcp 仅在本地配置了 MCP 服务的环境可用。
        @Autowired(required = false)
        @Qualifier("mcpToolCallbacks") // McpToolCallbackAutoConfiguration#mcpToolCallbacks 定义的 SYNC provider
        private ToolCallbackProvider toolCallbackProvider;

        public String doChatWithMcp(String message, String chatId, String userId) {
                if (toolCallbackProvider == null) {
                        throw new IllegalStateException("MCP 客户端未启用（当前环境不提供搜图/搜视频工具）");
                }
                ChatResponse response = chatClient.prompt().user(message)
                                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId)
                                                .param(UserProfileAdvisor.USER_ID, effectiveUserId(userId)))
                                // 开启日志，便于观察效果
                                .advisors(new LoggingAdvisor())
                                // ⚠️ 必须用 .toolCallbacks()：.tools() 会反射扫描 @Tool 方法，
                                // 而 ToolCallbackProvider 不是 @Tool POJO，会抛 IllegalStateException。
                                .toolCallbacks(toolCallbackProvider)
                                .toolCallbacks(profileToolCallbacks)
                                .toolContext(Map.of(UserProfileTools.USER_ID, effectiveUserId(userId)))
                                .call().chatResponse();
                String content = response.getResult().getOutput().getText();
                log.info("content: {}", content);
                return content;
        }

        /**
         * 组装一次对话请求
         *
         * @param userText 用户输入
         * @param chatId   会话 ID
         * @param userId   用户 ID（正常 HTTP 入口已强制登录，必为非空；这里仍容忍空值以防直接调用）
         * @return 组装好的请求
         */
        private ChatClientRequestSpec prepare(String userText, String chatId, String userId) {
                return chatClient.prompt().user(userText)
                                // 会话 ID：MessageChatMemoryAdvisor 靠它存取记忆
                                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, chatId)
                                                // 用户 ID：UserProfileAdvisor 靠它读画像
                                                .param(UserProfileAdvisor.USER_ID, effectiveUserId(userId)))
                                // 画像工具：模型自己决定什么时候调用
                                .toolCallbacks(profileToolCallbacks)
                                // ToolContext：调用工具时由 Spring AI 注入给工具方法，
                                // 工具方法靠它拿到「当前是哪个用户」
                                .toolContext(Map.of(UserProfileTools.USER_ID, effectiveUserId(userId)));
        }

        /**
         * 归一化 userId
         *
         * 空值统一成空字符串，而不是 null：
         * Advisor 参数最终会放进 ConcurrentHashMap（不允许 null 值），
         * ToolContext 的 value 也不允许 null。
         * 空串在 UserProfileAdvisor / UserProfileTools 里都会被当成「没有用户身份」。
         */
        private static String effectiveUserId(String userId) {
                return (userId == null || userId.isBlank()) ? "" : userId.trim();
        }

}
