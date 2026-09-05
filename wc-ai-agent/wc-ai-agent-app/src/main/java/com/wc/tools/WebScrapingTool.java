package com.wc.tools;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

public class WebScrapingTool
{

    @Tool(description = "Scrape the content of a web page")
    public String scrapeWebPage(@ToolParam(description = "URL of the web page to scrape") String url)
    {
        try
        {
            System.out.println("当前调用工具为：网页抓取");
            Document doc = Jsoup.connect(url).get();
            return doc.html();
        } catch (IOException e)
        {
            return "Error scraping web page: " + e.getMessage();
        }
    }
}
