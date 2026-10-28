package com.wc.rag;

import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 恋爱知识库检索工具 —— 由模型自己决定「要不要查知识库」。
 *
 * <p>
 * 为什么用「工具」而不是每次对话都自动检索（Advisor 方式）？
 * <ul>
 * <li>自动检索 = 每句话都要把问题向量化一次并查库，哪怕用户只是打招呼；</li>
 * <li>工具方式 = 模型自己判断这次要不要查，只有真的需要专业方法/话术时才付这次成本；</li>
 * <li>顺带的好处：模型可以先把口语化的问题改写成更适合检索的短句，再拿来查。</li>
 * </ul>
 *
 * <p>
 * 知识库本体在 pgvector 向量库（{@code public.vector_store} 表）里，
 * 已经是向量化好的恋爱知识（热恋 / 恋爱 / 分手三个状态下的应对方法、话术、建议）。
 *
 * <p>
 * 工具返回的是纯文本，Spring AI 会把它作为 ToolResponse 交回模型，
 * 模型据此作答；用户最终看到的是模型的回答，不会看到检索原文。
 */
@Component
@Slf4j
public class LoveAppKnowledgeTools
{
    /** 单条片段最多返回多少字，防止知识库变大后一次塞太多进上下文 */
    private static final int MAX_CHUNK_LENGTH = 800;

    /** 一次最多返回多少字 */
    private static final int MAX_TOTAL_LENGTH = 3000;

    private final VectorStore vectorStore;

    /** 一次检索召回几个片段 */
    private final int topK;

    /** 相似度阈值（0~1，越高越严格） */
    private final double similarityThreshold;

    public LoveAppKnowledgeTools(VectorStore vectorStore,
            @Value("${love.rag.top-k:3}") int topK,
            @Value("${love.rag.similarity-threshold:0.5}") double similarityThreshold)
    {
        this.vectorStore = vectorStore;
        this.topK = topK;
        this.similarityThreshold = similarityThreshold;
    }

    /**
     * 检索恋爱知识库
     *
     * @param query 检索关键词，取用户问题的核心内容
     * @return 检索到的知识片段（给模型看的文本）
     */
    @Tool(description = """
            检索恋爱知识库。知识库里是热恋、恋爱中、分手三种关系状态下的具体应对方法、
            沟通话术和注意事项。
            当用户问的是「这种情况怎么办」「怎么回复对方」「这句话该怎么回」
            「怎么挽回」「怎么推进关系」这类需要具体方法或话术的问题时调用。
            闲聊、打招呼、单纯宣泄情绪、以及你能直接凭常识回答的问题不要调用。
            query 传用户问题的核心内容，可以改写成更适合检索的短句
            （例如用户说「他三天没回消息了」→ query「对方不回复消息」）。
            """)
    public String searchKnowledgeBase(String query)
    {
        if (query == null || query.isBlank())
        {
            return "检索词为空，无法检索知识库。请基于你自己的经验回答。";
        }

        SearchRequest request = SearchRequest.builder()
                .query(query.trim())
                .topK(topK)
                .similarityThreshold(similarityThreshold)
                .build();

        List<Document> documents = this.vectorStore.similaritySearch(request);
        log.info("知识库检索: query={}, 命中 {} 条", query.trim(), documents.size());

        if (documents.isEmpty())
        {
            return "知识库里没有检索到相关内容。请基于你自己的恋爱顾问经验回答，"
                    + "并说明这条建议是通用建议而不是知识库里的既有方法。";
        }

        StringBuilder sb = new StringBuilder("知识库检索结果（" + documents.size() + " 条）：\n");
        for (Document document : documents)
        {
            sb.append("- ");
            String title = metadata(document, "title");
            String status = metadata(document, "status");
            if (title != null)
            {
                sb.append("【").append(title).append("】");
            }
            if (status != null)
            {
                sb.append("（适用状态：").append(status).append("）");
            }
            sb.append(truncate(document.getText())).append("\n");

            if (sb.length() >= MAX_TOTAL_LENGTH)
            {
                break;
            }
        }
        return sb.toString();
    }

    /** 取 metadata 里的字符串值；没有或不是字符串就返回 null */
    private static String metadata(Document document, String key)
    {
        Object value = document.getMetadata().get(key);
        return (value instanceof String s && !s.isBlank()) ? s : null;
    }

    /** 截断过长的片段 */
    private static String truncate(String text)
    {
        if (text == null)
        {
            return "";
        }
        return text.length() <= MAX_CHUNK_LENGTH ? text : text.substring(0, MAX_CHUNK_LENGTH) + "…";
    }
}
