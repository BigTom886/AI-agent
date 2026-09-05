package com.wc.demo.invoke;
import java.util.Arrays;
import java.util.Collections;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.alibaba.dashscope.common.MultiModalMessage;
import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.exception.ApiException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.exception.UploadFileException;
import com.alibaba.dashscope.utils.Constants;

/**
 * 阿里云 DashScope SDK 多模态对话调用示例
 * <p>
 * 核心功能：发送一张图片 + 一段文本提问，获取 AI 的回答
 * <p>
 * 运行前提：
 * 1. 环境变量 DASHSCOPE_API_KEY 需设置为阿里云 DashScope 的 API 密钥
 * 2. Maven 依赖需引入 com.alibaba:dashscope-sdk-java
 * 3. 网络需能访问 dashscope.aliyuncs.com
 */
public class AIsdkInvoke {

    // 静态初始化块：在类加载时设置 DashScope API 的基础 URL，指向阿里云 DashScope 服务的 v1 接口端点
    static {Constants.baseHttpApiUrl="https://dashscope.aliyuncs.com/api/v1";}

    /**
     * 多模态对话调用方法
     * <p>
     * 流程：创建客户端 → 构建用户消息(图片+文本) → 构建请求参数 → 发起同步调用 → 输出结果
     *
     * @throws ApiException         API 调用异常
     * @throws NoApiKeyException    未配置 API Key 异常
     * @throws UploadFileException  文件上传异常
     */
    public static void simpleMultiModalConversationCall()
            throws ApiException, NoApiKeyException, UploadFileException {
        // ① 创建多模态对话客户端
        MultiModalConversation conv = new MultiModalConversation();

        // ② 构建用户消息：USER 角色，内容包含一张图片 URL 和一段文本提问
        MultiModalMessage userMessage = MultiModalMessage.builder().role(Role.USER.getValue())
                .content(Arrays.asList(
                        // 图片部分：狗和女孩的合影
                        Collections.singletonMap("image", "https://help-static-aliyun-doc.aliyuncs.com/file-manage-files/zh-CN/20241022/emyrja/dog_and_girl.jpeg"),
                        // 文本部分：对图片的提问
                        Collections.singletonMap("text", "图中描绘的是什么景象?"))).build();

        // ③ 构建请求参数：API Key（从 TestApiKey 接口获取）、模型名称、消息列表
        MultiModalConversationParam param = MultiModalConversationParam.builder()
                .apiKey(TestApiKey.API_KEY)
                .model("qwen3.7-plus")
                .messages(Arrays.asList(userMessage))
                .build();

        // ④ 发起同步调用，获取结果
        MultiModalConversationResult result = conv.call(param);

        // 解析响应结构：输出 → 选项列表 → 第一个选项 → 消息 → 内容列表 → 第一项 → text 字段
        // 注意：链式调用缺少空值保护，生产代码应加入非空检查以避免 NullPointerException
        System.out.println(result.getOutput().getChoices().get(0).getMessage().getContent().get(0).get("text"));
    }

    public static void main(String[] args) {
        try {
            // 调用多模态对话方法
            simpleMultiModalConversationCall();
        } catch (ApiException | NoApiKeyException | UploadFileException e) {
            // 捕获三类异常并打印错误信息
            System.out.println(e.getMessage());
        }
        // 强制退出 JVM
        System.exit(0);
    }
}
