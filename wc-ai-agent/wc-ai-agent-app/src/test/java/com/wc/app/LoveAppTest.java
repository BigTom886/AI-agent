package com.wc.app;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import cn.hutool.core.lang.UUID;

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

        // chatId 必须是 UUID（接口层会做格式校验）
        String chatId = UUID.randomUUID().toString();

        // userId 决定这次对话用哪份跨会话画像；还没接登录体系时传 null 即可
        String result1 = loveApp.doChat("我是谁？", chatId, null);
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
        LoveReport loveReport = loveApp.generateLoveReport(message, chatId, null);
        Assertions.assertNotNull(loveReport);
    }

    private void testMessage(String message) {
        String chatId = UUID.randomUUID().toString();
        String answer = loveApp.doChatWithTools(message, chatId, null);
        Assertions.assertNotNull(answer);
    }

    @Test
    void doChatWithTools() {
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
        testMessage("你当前有哪些工具？");
    }

}
