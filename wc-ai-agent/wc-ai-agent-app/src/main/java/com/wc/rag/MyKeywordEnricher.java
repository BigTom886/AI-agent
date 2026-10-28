package com.wc.rag;

import java.util.List;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.model.transformer.KeywordMetadataEnricher;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;

/**
 * 文档关键词抽取器（基于 Spring AI 的 {@link KeywordMetadataEnricher}）。
 *
 * <p>
 * 在 RAG（检索增强生成）数据预处理阶段被调用：对传入的 {@link Document} 列表，
 * 借助阿里通义千问（DashScope）大模型，为每篇文档自动抽取关键词， 并写入 Document 的 metadata 中（key 为
 * "excerpt_keywords"）， 供下游（如混合检索、标签过滤）使用。
 *
 * <p>
 * 作为 Spring 组件，应用启动时由容器自动实例化；依赖的 {@link ChatModel} 会通过 {@link Resource} 按名称注入。
 *
 * @author wc
 */
@Component
public class MyKeywordEnricher
{

    /**
     * 通义千问（DashScope）的 ChatModel Bean，用于调用大模型抽取关键词。 按 Bean
     * 名称（"dashscopeChatModel"）注入，避免与其他 ChatModel 冲突。
     */
    @Resource
    private ChatModel dashscopeChatModel;

    /**
     * 为给定的文档列表抽取关键词，并写入每篇文档的 metadata。
     *
     * <p>
     * 处理流程：
     * <ol>
     * <li>构造一个 {@link KeywordMetadataEnricher}，指定抽取 5 个关键词</li>
     * <li>对列表中的每篇 Document 调用 LLM，返回结果写入 metadata["excerpt_keywords"]</li>
     * <li>返回处理后的文档列表（注意：原对象被就地修改，而非新建）</li>
     * </ol>
     *
     * <p>
     * 注意：每篇文档会触发一次 LLM 调用，调用次数与文档数量线性相关，
     * 文档量大时建议配合分片器使用，避免单次请求文本过长。这里其实就是利用大模型来提取文档的关键字信息
     *
     * @param documents 待处理的文档列表，不可为 null
     * @return 已写入 "excerpt_keywords" 元数据的同一批文档
     */
    public List<Document> enrichDocunments(List<Document> documents)
    {
        // 指定每篇文档抽取 5 个关键词；PromptTemplate 在 KeywordMetadataEnricher 构造时自动生成
        KeywordMetadataEnricher enricher = new KeywordMetadataEnricher(this.dashscopeChatModel, 5);
        // 逐篇文档调用大模型，并将返回结果写入 metadata 的 "excerpt_keywords" 字段
        return enricher.apply(documents);
    }

}
