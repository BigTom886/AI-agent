package com.wc.app;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import reactor.core.publisher.Flux;

@SpringBootTest
public class LoveAppTest {

    @Autowired
    private LoveApp loveApp;

    @BeforeAll
    static void fixEncoding() {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
    }

    @Test
    void testDoChat() {
        // 两轮对话必须用同一个 chatId，MessageChatMemoryAdvisor 才会携带历史消息

        // 第一轮：自我介绍
        System.out.print("第一轮 AI 回复：");
        Flux<String> flux1 = loveApp.doChat("你好，我是孙悟空", "chat-001");
        // doOnNext 挂在流上打印每个片段，blockLast() 阻塞等待流结束
        flux1.doOnNext(chunk -> System.out.print(chunk)).blockLast();
        System.out.println("\n【第一轮输出完成】");

        // 第二轮：测试记忆 —— AI 应该能记住"孙悟空"
        System.out.print("第二轮 AI 回复：");
        Flux<String> flux2 = loveApp.doChat("我是谁？", "chat-001");
        flux2.doOnNext(chunk -> System.out.print(chunk)).blockLast();
        System.out.println("\n【第二轮输出完成】");
    }
}
