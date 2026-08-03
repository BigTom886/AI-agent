package com.wc.demo.invoke;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.CommandLineRunner;

import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * Spring AI 框架调用AI大模型
 * 通过 DashScope 的 OpenAI 兼容模式接入
 */
//@Component
public class SpringAiinvoke implements CommandLineRunner {

    @Resource
    private ChatModel dashscopeChatModel;

    @Override
    public void run(String... args) throws Exception {
        try {
            String response = dashscopeChatModel.call(new Prompt("你好，我是王城"))
                    .getResult()
                    .getOutput()
                    .getText();
            System.out.println("AI 回复: " + response);
        } catch (Exception e) {
            System.err.println("调用 AI 模型失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
