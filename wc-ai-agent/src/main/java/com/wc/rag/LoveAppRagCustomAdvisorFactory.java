package com.wc.rag;

import java.nio.file.DirectoryStream.Filter;

import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter.Expression;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

import lombok.extern.slf4j.Slf4j;

/**
 * 文档检索增强
 * LoveAppRagCustomAdvisorFactory
 */
@Slf4j
public class LoveAppRagCustomAdvisorFactory {
        public static Advisor createLoveAppRagCustomAdvisor(VectorStore vectorStore, String status) {
                Expression expression = new FilterExpressionBuilder()
                                .eq("status", status)
                                .build();
                DocumentRetriever documentRetriever = VectorStoreDocumentRetriever.builder()
                                .vectorStore(vectorStore)
                                .filterExpression(expression) // 过滤条件
                                .similarityThreshold(0.5) // 相似度阈值
                                .topK(3) // 返回文档数量
                                .build();
                return RetrievalAugmentationAdvisor.builder()
                                .documentRetriever(documentRetriever)
                                .build();

        }
}
