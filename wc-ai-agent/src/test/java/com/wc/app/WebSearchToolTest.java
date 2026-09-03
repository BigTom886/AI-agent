package com.wc.app;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import com.wc.tools.WebSearchTool;

@SpringBootTest
public class WebSearchToolTest
{

    @Value("${search-api.api-key}")
    private String searchApiKey;

    @BeforeAll
    static void fixEncoding()
    {
        // 强制 System.out 使用 UTF-8,避免 IDE 控制台按 GBK 解码中文导致乱码
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
    }

    @Test
    public void testSearchWeb()
    {
        WebSearchTool tool = new WebSearchTool(searchApiKey);
        String query = "程序员鱼皮编程导航 codefather.cn";
        String result = tool.searchWeb(query);
        System.out.println("Search result: " + result);
        assertNotNull(result);
    }
}
