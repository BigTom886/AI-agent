package com.wc.demo.invoke;

import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;

/**
 * PromptTemplate 使用示例 —— 演示 6 种典型用法。
 *
 * <p>
 * 与 {@link SpringAiinvoke} 类似，注入 {@link ChatModel} 直接调用；
 * 加 {@code @Component} 即可在 SpringBoot 启动后自动执行，
 * 注释掉 {@code @Component} 后可作为普通类手动调用其方法。
 */
// @Component
public class PromptTemplateDemo implements CommandLineRunner {

    @Resource
    private ChatModel dashscopeChatModel;

    @Override
    public void run(String... args) throws Exception {
        demo1_basicRender();
        demo2_createPromptWithInternalVars();
        demo3_createPromptWithExternalVars();
        demo4_createPromptWithChatOptions();
        demo5_createPromptWithMedia();
        demo6_dynamicTemplate();
    }

    /**
     * 场景 1：仅渲染模板字符串（不调用模型）。
     * 用途：在业务代码中拼装最终提示词，做日志 / 调试用。
     */
    private void demo1_basicRender() {
        System.out.println("\n===== Demo 1: render() 仅渲染字符串 =====");

        PromptTemplate tpl = new PromptTemplate("你好 {name}，今天是 {day}，请帮我 {task}");
        tpl.addVariable("name", "王城");
        tpl.addVariable("day", "周三");
        // task 在外部传入
        String prompt = tpl.render(Map.of("task", "review 这段代码"));

        System.out.println("渲染结果:\n" + prompt);
    }

    /**
     * 场景 2：内置变量 + create() 直接构造 Prompt 调模型。
     */
    private void demo2_createPromptWithInternalVars() {
        System.out.println("\n===== Demo 2: create() 内置变量调模型 =====");

        PromptTemplate tpl = new PromptTemplate(
                "你是一位资深 Java 工程师，请用一句话解释 {name} 的作用");
        tpl.addVariable("name", "ReentrantLock");

        Prompt prompt = tpl.create();
        String reply = dashscopeChatModel.call(prompt).getResult().getOutput().getText();
        System.out.println("AI 回复:\n" + reply);
    }

    /**
     * 场景 3：同一个模板、不同变量 —— 调用方传入变量优先于内置变量。
     */
    private void demo3_createPromptWithExternalVars() {
        System.out.println("\n===== Demo 3: create(Map) 调用方变量覆盖 =====");

        PromptTemplate tpl = new PromptTemplate(
                "把以下内容翻译成 {language}：\n{content}");
        tpl.addVariable("language", "中文");
        tpl.addVariable("content", "Hello, world!");

        // 调用方传入新的 content，覆盖内置
        Prompt prompt = tpl.create(Map.of("content", "The quick brown fox jumps over the lazy dog."));

        String reply = dashscopeChatModel.call(prompt).getResult().getOutput().getText();
        System.out.println("AI 回复:\n" + reply);
    }

    /**
     * 场景 4：自定义 ChatOptions（温度、模型名等）。
     * 用途：同一套模板用不同温度 / 模型对比效果。
     */
    private void demo4_createPromptWithChatOptions() {
        System.out.println("\n===== Demo 4: create(Map, ChatOptions) 自定义选项 =====");

        PromptTemplate tpl = new PromptTemplate(
                "请用 {style} 的语气写一句关于 {topic} 的话，不超过 30 字");

        ChatOptions options = ChatOptions.builder()
                .temperature(0.9) // 高温度 = 更发散
                .build();

        Prompt prompt = tpl.create(
                Map.of("style", "幽默", "topic", "Spring AI"),
                options);

        String reply = dashscopeChatModel.call(prompt).getResult().getOutput().getText();
        System.out.println("AI 回复:\n" + reply);
    }

    /**
     * 场景 5：多模态消息 —— 模板渲染的文本 + 附加的 Media（图片）。
     */
    private void demo5_createPromptWithMedia() {
        System.out.println("\n===== Demo 5: createMessage(List<Media>) 多模态 =====");

        PromptTemplate tpl = new PromptTemplate(
                "请仔细看这张图片，告诉我图中有什么（用中文回答）");

        // 实际项目中通常这样构造：
        // Media image = new Media(MimeTypeUtils.IMAGE_PNG, new
        // FileSystemResource("image.png"));
        // 这里为了演示只打印文本
        List<Media> mediaList = List.of(); // 空列表也行，只是演示签名

        Message message = tpl.createMessage(mediaList);
        Prompt prompt = new Prompt(message);

        System.out.println("构造的 Prompt 内容:\n" + prompt.getContents());
        // 真正调模型时：
        // String reply =
        // dashscopeChatModel.call(prompt).getResult().getOutput().getText();
    }

    /**
     * 场景 6：动态切换模板内容 —— setTemplate() 在运行时换模板。
     * 用途：根据用户意图路由到不同 system prompt。
     */
    private void demo6_dynamicTemplate() {
        System.out.println("\n===== Demo 6: setTemplate() 动态切换模板 =====");

        PromptTemplate tpl = new PromptTemplate();
        tpl.addVariable("user_input", "北京今天天气怎么样？");

        // 根据场景切换模板
        tpl.setTemplate("你是一位天气预报员，请回答：{user_input}");
        String weatherPrompt = tpl.render();
        System.out.println("天气模板:\n" + weatherPrompt);

        tpl.setTemplate("你是一位严谨的律师，请从法律角度回答：{user_input}");
        String lawyerPrompt = tpl.render();
        System.out.println("律师模板:\n" + lawyerPrompt);
    }
}