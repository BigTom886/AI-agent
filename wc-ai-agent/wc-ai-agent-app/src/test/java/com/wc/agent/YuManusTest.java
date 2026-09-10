package com.wc.agent;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import jakarta.annotation.Resource;

@SpringBootTest
class YuManusTest {

    @Resource
    private YuManus yuManus;

    @BeforeAll
    static void fixEncoding() {
        // 强制 System.out 使用 UTF-8,避免 IDE 控制台按 GBK 解码中文导致乱码
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
    }

    // 我的另一半居住在上海静安区，请帮我找到 5 公里内合适的约会地点，
    // 并结合一些网络图片，制定一份详细的约会计划，
    // 并以 PDF 格式输出
    @Test
    void run() {
        String userPrompt = """
                我的另一半居住在上海静安区，请帮我找到 5 公里内合适的约会地点，
                并结合一些网络图片，制定一份详细的约会计划，
                并以 PDF 格式输出
                        """;
        String answer = yuManus.run(userPrompt);
        Assertions.assertNotNull(answer);
    }
}
