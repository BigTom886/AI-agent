package com.wc.app;

import com.esotericsoftware.minlog.Log;
import com.wc.demo.rag.MultiQueryExpanderDemo;
import com.wc.demo.rag.QueryReWriter;
import com.wc.rag.LoveAppDocumentLoader;
import com.wc.rag.LoveAppRagCustomAdvisorFactory;
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
import org.springframework.ai.rag.Query;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import cn.hutool.core.lang.UUID;

import jakarta.annotation.Resource;

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
    void doChatWithReport() {

        String chatId = UUID.randomUUID().toString();
        // 第一轮
        String message = "你好，我是豆包，我想让另一半（用户）更爱我，但我不知道该怎么做";
        LoveApp.LoveReport loveReport = loveApp.generateLoveReport(message, chatId);
        Assertions.assertNotNull(loveReport);
    }

    @Test
    void doChatWithRag() {
        String chatId = UUID.randomUUID().toString();
        String message = "我已经结婚了，但是婚后关系不太亲密，怎么办？";
        String answer = loveApp.doChatWithRag(message, chatId);
        Assertions.assertNotNull(answer);
    }

    @Resource
    VectorStore pgVectorVectorStore;

    @Test
    void test1() {
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
    void test2() {

        List<Document> documents = loveAppDocumentLoader.loadMarkdowns();
        // 添加文档
        pgVectorVectorStore.add(documents);
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
    void test3() {
        List<Document> documents = loveAppDocumentLoader.loadMarkdowns();

        // 自主切分(这里是按token切分，实际可按业务需求自定义切分规则)
        // List<Document> splitedDocuments =
        // myTokenTextSplitter.spliteCustomized(documents);

        // 利用大模型提取文档块关键信息
        List<Document> enrichedDocuments = myKeywordEnricher.enrichDocunments(documents);

        for (Document doc : enrichedDocuments) {
            System.out.println("=== splited document ===");
            System.out.println("[text] " + doc.getText() + System.lineSeparator() + "[metadata] " + doc.getMetadata());
        }
    }

    @Resource
    MultiQueryExpanderDemo multiQueryExpanderDemo;

    @Test
    void testQueryExpander() {
        List<Query> queries = multiQueryExpanderDemo.expand("谁是程序员鱼皮啊啊啊啊？？？？");
    }

    @Resource
    QueryReWriter queryReWriter;

    @Test
    void testQueryReWriter() {
        String query = queryReWriter.doQueryRewrite("谁是程序员鱼皮啊啊啊啊？？？？");
    }

    @Test
    void testRagDocument() {
        String chatId = UUID.randomUUID().toString();
        String mString = loveApp.doRagAdvisor("我已经结婚了，但是婚后关系不太亲密，应该怎么办？", chatId, "单身");
        Log.info("mString:" + mString);
    }
}

// 12.13 使用deepseek