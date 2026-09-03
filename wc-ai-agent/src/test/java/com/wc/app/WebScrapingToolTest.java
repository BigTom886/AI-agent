package com.wc.app;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.wc.tools.WebScrapingTool;

public class WebScrapingToolTest
{

    @BeforeAll
    static void fixEncoding()
    {
        // 强制 System.out 使用 UTF-8,避免 IDE 控制台按 GBK 解码中文导致乱码
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
    }

    @Test
    public void testScrapeWebPage()
    {
        WebScrapingTool tool = new WebScrapingTool();
        String url = "https://www.codefather.cn";
        String result = tool.scrapeWebPage(url);
        System.out.println("Scraped web page content: " + result);
        assertNotNull(result);
    }
}
