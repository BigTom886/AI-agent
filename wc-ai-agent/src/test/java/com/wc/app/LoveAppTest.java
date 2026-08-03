package com.wc.app;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

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
        String result = loveApp.doChat("你好，我是孙悟空", "001");
        System.out.println("AI 回复：" + result);

        String result1 = loveApp.doChat("我是谁？", "001");
        System.out.println("AI 回复：" + result1);

    }
}
