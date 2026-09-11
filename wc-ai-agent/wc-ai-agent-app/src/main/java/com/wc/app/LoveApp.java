package com.wc.app;

import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.AdvisorSpec;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.wc.chatmemory.FileBasedChatMemory;
import com.wc.demo.invoke.LoggingAdvisor;
import com.wc.demo.invoke.Re2Advisor;
import com.wc.rag.LoveAppRagCustomAdvisorFactory;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

/**
 * 恋爱顾问应用 —— 基于 Spring AI ChatClient 与大模型交互
 *
 * ChatClient 核心概念： ChatClient 是 Spring AI 提供的流畅 API（Fluent
 * API）客户端，用于以声明式、链式调用的方式 向 AI 模型发送提示词并获取响应，设计理念类似于 Spring WebFlux 中的 WebClient。
 *
 * 核心组件： - ChatModel —— 底层模型适配器，本项目通过 OpenAI 兼容模式接入 DashScope（通义千问） - Advisors
 * —— 拦截器链，类似 Servlet Filter，可在请求前后做增强（如记忆、日志、重试等） - ChatMemory ——
 * 对话记忆，维护多轮对话上下文
 *
 * ChatClient 关键 API 速览： - ChatClient.builder(chatModel) → 创建构建器，绑定底层 ChatModel
 * - .defaultSystem("...") → 设置默认系统提示词 - .defaultAdvisors(...) → 注册默认
 * Advisor（拦截器） - .prompt().user("...") → 设置用户消息 - .prompt().system("...") →
 * 设置本次系统消息（覆盖默认） - .call() → 同步调用，返回 ChatResponse - .stream() → 流式调用，返回
 * Flux<String> - .content() → 从响应中提取纯文本内容
 */
@Component
@Slf4j
public class LoveApp {

        /** ChatClient 实例，用于与 AI 模型交互 */
        private final ChatClient chatClient;

        /**
         * 对话记忆 —— 基于滑动窗口的实现 MessageWindowChatMemory 会保留最近 N 轮对话，避免上下文过长导致 token 超限
         */
        ChatMemory chatMemory = MessageWindowChatMemory.builder().maxMessages(10).build();

        /**
         * 系统提示词 —— 定义 AI 的角色和行为规范 通过 ChatClient.builder().defaultSystem() 设置，每次对话都会自动携带
         */
        private static final String SYSTEM_PROMPT = "你是一个恋爱顾问，名字叫小爱。";

        /**
         * 构造函数 —— 注入 ChatModel 并构建 ChatClient
         *
         * Spring AI 自动配置会根据 application.yml 中的模型配置注入 ChatModel Bean， 然后我们手动构建
         * ChatClient 来添加 Advisor 链（如对话记忆增强器）。
         *
         * 为什么不直接注入 ChatClient？因为自动配置的 ChatClient 不带 Advisor， 我们需要通过 builder 自定义添加
         * MessageChatMemoryAdvisor。
         *
         * @param chatModel Spring AI 自动注入的底层模型适配器（DashScope OpenAI 兼容模式）
         */
        public LoveApp(ChatModel chatModel) {

                // 初始化基于文件的对话记忆
                String memoryDir = System.getProperty("user.dir") + "/chat_memory"; // 存储对话记忆的目录
                // System.out.println("==== LoveApp 构造器被调用 ====");

                log.info("初始化对话记忆目录: {}", memoryDir);
                ChatMemory fileBasedChatMemory = new FileBasedChatMemory(memoryDir);

                this.chatClient = ChatClient.builder(chatModel)
                                // 设置默认系统提示词，定义 AI 的角色和行为
                                .defaultSystem(SYSTEM_PROMPT)
                                // 注册对话记忆 Advisor：每次请求自动携带历史消息，响应后自动保存新消息
                                .defaultAdvisors(
                                                // 重读 Advisor：在用户消息后追加"再读一遍问题"，提升推理质量
                                                // new Re2Advisor(),
                                                // 对话记忆 Advisor：每次请求自动携带历史消息，响应后自动保存新消息
                                                MessageChatMemoryAdvisor.builder(fileBasedChatMemory).build(),
                                                // 日志 Advisor：在请求前后打印日志
                                                new LoggingAdvisor())
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
         * @return AI 模型的文本响应
         */
        public String doChat(String userInput, String chatId) {

                String conversationId = chatId;

                return this.chatClient.prompt().user(userInput)
                                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId)) //
                                // stream()流式返回
                                // call()同步返回
                                .call().content();
        }

        public Flux<String> doChatByStream(String message, String chatId) {
                return chatClient.prompt().user(message)
                                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                                .stream()
                                .content();
        }

        // 恋爱报告类
        record LoveReport(String title, List<String> suggestions) {
        }

        public LoveReport generateLoveReport(String userInput, String chatId) {
                String conversationId = chatId;

                String reportText = this.chatClient.prompt()
                                .system(SYSTEM_PROMPT + " 每次对话后都要生成恋爱结果，标题为{用户名}的恋爱报告，内容为建议列表").user(userInput)
                                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId)).call().content();

                // .advisors(new Consumer<AdvisorSpec>() { ←正确接口名
                // @Override ← 重写的是 Consumer#accept
                // public void accept(AdvisorSpec a) { ← 参数类型是 AdvisorSpec
                // a.param(ChatMemory.CONVERSATION_ID, conversationId); ← 这是普通调用，不是 override
                // }
                // })

                // 将报告文本按行拆分为建议列表
                List<String> suggestions = reportText.lines().toList();

                // log.info("生成恋爱报告: {}", suggestions);

                return new LoveReport("恋爱顾问报告", suggestions);
        }

        @Resource
        private VectorStore loveAppVectorStore;

        public String doChatWithRag(String message, String chatId) {
                ChatResponse chatResponse = chatClient.prompt().user(message)
                                // 1.1.8 起，CHAT_MEMORY_RETRIEVE_SIZE_KEY 已废弃；
                                // 窗口大小由 ChatMemory 实现（如 MessageWindowChatMemory.maxMessages）控制
                                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                                // 开启日志，便于观察效果(chatClient已经注册过LoggingAdvisor)
                                // .advisors(new LoggingAdvisor())
                                // 应用知识库问答（1.1.x 起 QuestionAnswerAdvisor 被模块化的 RetrievalAugmentationAdvisor 替代）
                                .advisors(RetrievalAugmentationAdvisor.builder()
                                                .documentRetriever(VectorStoreDocumentRetriever.builder()
                                                                .vectorStore(loveAppVectorStore).build())
                                                .build())
                                .call().chatResponse();
                String content = chatResponse.getResult().getOutput().getText();
                // log.info("content: {}", content);
                return content;
        }

        /**
         * 用自定义 RAG Advisor 执行对话 —— 只检索 status 匹配的文档
         *
         * @param message 用户输入
         * @param chatId  会话 ID（用于对话记忆隔离）
         * @param status  要过滤的文档 status（如 "published"）
         * @return AI 回复内容
         */

        @Resource
        VectorStore pgVectorVectorStore;

        public String doRagAdvisor(String message, String chatId, String status) {

                // 1. 用工厂的静态方法构造自定义 Advisor：
                // - 过滤 status 字段
                // - 相似度阈值 0.5
                // - topK=3
                Advisor customAdvisor = LoveAppRagCustomAdvisorFactory
                                .createLoveAppRagCustomAdvisor(pgVectorVectorStore, status);

                // 2. 调 ChatClient，叠加对话记忆 + 自定义 Advisor
                ChatResponse chatResponse = chatClient.prompt().user(message)
                                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId)) // 会话记忆
                                .advisors(customAdvisor) // 自定义 RAG
                                .call().chatResponse();

                // 3. 提取文本内容返回
                return chatResponse.getResult().getOutput().getText();
        }

        @Resource
        private ToolCallback[] allTools;

        public String doChatWithTools(String message, String chatId) {
                ChatResponse response = chatClient.prompt().user(message)
                                // 已包装好的 ToolCallback[] 必须用 .toolCallbacks(),用 .tools() 会再次反射找 @Tool 导致失败
                                .toolCallbacks(allTools).call().chatResponse();
                String content = response.getResult().getOutput().getText();
                // log.info("content: {}", content);
                return content;
        }

        @Resource
        // Spring AI通过yml配置文件自动注入mcpToolCallbacks对象
        @Qualifier("mcpToolCallbacks") // McpToolCallbackAutoConfiguration#mcpToolCallbacks 定义的 SYNC provider
        private ToolCallbackProvider toolCallbackProvider;

        public String doChatWithMcp(String message, String chatId) {
                ChatResponse response = chatClient.prompt().user(message)
                                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                                // 开启日志，便于观察效果
                                .advisors(new LoggingAdvisor())
                                // ⚠️ 必须用 .toolCallbacks()：.tools() 会反射扫描 @Tool 方法，
                                // 而 ToolCallbackProvider 不是 @Tool POJO，会抛 IllegalStateException。
                                .toolCallbacks(toolCallbackProvider).call().chatResponse();
                String content = response.getResult().getOutput().getText();
                log.info("content: {}", content);
                return content;
        }

}