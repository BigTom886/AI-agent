package com.wc.app;

import com.wc.rag.LoveAppDocumentLoader;
import com.wc.rag.MyKeywordEnricher;
import com.wc.rag.MyTokenTextSplitter;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import cn.hutool.core.lang.UUID;
import jakarta.annotation.Resource;

@SpringBootTest
public class LoveAppTest
{

    @Autowired
    private LoveApp loveApp;

    @BeforeAll
    static void fixEncoding()
    {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
    }

    @Test
    void testDoChat()
    {
        // 两轮对话必须用同一个 chatId，MessageChatMemoryAdvisor 才会携带历史消息

        // 同步调用
        // String result = loveApp.doChat("你好，我是韩跑跑", "001");
        // System.out.println("AI 回复：" + result);

        String result1 = loveApp.doChat("我是谁？", "001");
        // System.out.println("AI 回复：" + result1);

        /*
         * 流式调用 // 第一轮：自我介绍 System.out.print("第一轮 AI 回复："); Flux<String> flux1 =
         * loveApp.doChat("你好，我是韩立", "chat-001"); // doOnNext 挂在流上打印每个片段，blockLast()
         * 阻塞等待流结束 flux1.doOnNext(chunk -> System.out.print(chunk)).blockLast();
         * System.out.println("\n【第一轮输出完成】");
         * 
         * // 第二轮：测试记忆 —— AI 应该能记住"孙悟空" System.out.print("第二轮 AI 回复："); Flux<String>
         * flux2 = loveApp.doChat("我是谁？", "chat-001"); flux2.doOnNext(chunk ->
         * System.out.print(chunk)).blockLast(); System.out.println("\n【第二轮输出完成】");
         * 
         */
    }

    @Test
    void doChatWithReport()
    {

        String chatId = UUID.randomUUID().toString();
        // 第一轮
        String message = "你好，我是豆包，我想让另一半（用户）更爱我，但我不知道该怎么做";
        LoveApp.LoveReport loveReport = loveApp.generateLoveReport(message, chatId);
        Assertions.assertNotNull(loveReport);
    }

    @Test
    void doChatWithRag()
    {
        String chatId = UUID.randomUUID().toString();
        String message = "我已经结婚了，但是婚后关系不太亲密，怎么办？";
        String answer = loveApp.doChatWithRag(message, chatId);
        Assertions.assertNotNull(answer);
    }

    @Resource
    VectorStore pgVectorVectorStore;

    @Test
    void test1()
    {
        List<Document> documents = List.of(
                new Document(
                        "Spring AI rocks!! Spring AI rocks!! Spring AI rocks!! Spring AI rocks!! Spring AI rocks!!",
                        Map.of("meta1", "meta1")),
                new Document("The World is Big and Salvation Lurks Around the Corner"),
                new Document("You walk forward facing the past and you turn back toward the future.",
                        Map.of("meta2", "meta2")));
        // 添加文档
        pgVectorVectorStore.add(documents);
        // 相似度查询
        List<Document> results = pgVectorVectorStore
                .similaritySearch(SearchRequest.builder().query("Spring").topK(5).build());
        Assertions.assertNotNull(results);
    }

    @Resource
    LoveAppDocumentLoader loveAppDocumentLoader;

    @Test
    void test2()
    {

        // List<Document> documents = loveAppDocumentLoader.loadMarkdowns();
        // // 添加文档
        // pgVectorVectorStore.add(documents);
        // 相似度查询
        List<Document> results = pgVectorVectorStore
                .similaritySearch(SearchRequest.builder().query("我已经结婚了，但是婚后关系不太亲密，应该怎么办？").topK(5).build());
        // 打印命中结果：内容 + 元数据（filename 是 loader 写入的，便于溯源到原 markdown）
        System.out.println("=== similarity search 命中 " + results.size() + " 条 ===");
        results.forEach(doc -> System.out
                .println("[text] " + doc.getText() + System.lineSeparator() + "[metadata] " + doc.getMetadata()));
        Assertions.assertNotNull(results);
    }

    @Resource
    MyTokenTextSplitter myTokenTextSplitter;

    @Resource
    MyKeywordEnricher myKeywordEnricher;

    @Test
    void test3()
    {
        List<Document> documents = loveAppDocumentLoader.loadMarkdowns();

        // 自主切分(这里是按token切分，实际可按业务需求自定义切分规则)
        // List<Document> splitedDocuments =
        // myTokenTextSplitter.spliteCustomized(documents);

        // 利用大模型提取文档块关键信息
        List<Document> enrichedDocuments = myKeywordEnricher.enrichDocunments(documents);

        for (Document doc : enrichedDocuments)
        {
            System.out.println("=== splited document ===");
            System.out.println("[text] " + doc.getText() + System.lineSeparator() + "[metadata] " + doc.getMetadata());

        }
    }

    private void testMessage(String message)
    {
        String chatId = UUID.randomUUID().toString();
        String answer = loveApp.doChatWithTools(message, chatId);
        Assertions.assertNotNull(answer);
    }

    @Test
    void doChatWithTools()
    {
        // 测试联网搜索问题的答案
        // testMessage("周末想带女朋友去上海约会，推荐几个适合情侣的小众打卡地？");

        // // 测试网页抓取：恋爱案例分析
        // testMessage("最近和对象吵架了，看看编程导航网站（codefather.cn）的其他情侣是怎么解决矛盾的？");

        // // 测试资源下载：图片下载
        // testMessage("直接下载一张适合做手机壁纸的星空情侣图片文件");

        // // 测试终端操作：执行代码
        // testMessage("执行 Python3 脚本来生成数据分析报告");

        // // 测试文件操作：保存用户档案
        // testMessage("保存我的恋爱档案为文件");

        // // 测试 PDF 生成
        testMessage("生成一份‘七夕约会计划’PDF，包含餐厅预订、活动流程和礼物清单");
    }

}
