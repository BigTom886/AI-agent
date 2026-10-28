package com.wc.rag;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import jakarta.annotation.Resource;

/**
 * 内存版向量库，依赖 EmbeddingModel。
 * 生产环境不使用 RAG/向量检索功能，prod profile 下不创建该 Bean。
 */
@Configuration
@Profile("!prod")
public class LoveAppVectorStoreConfig {

    @Resource
    private LoveAppDocumentLoader loveAppDocumentLoader;

    @Bean
    VectorStore loveAppVectorStore(EmbeddingModel dashscopeEmbeddingModel) {
        // 构造内存版向量库（基于余弦相似度）
        VectorStore simpleVectorStore = SimpleVectorStore.builder(dashscopeEmbeddingModel).build();
        // ========== 启动期不加载/嵌入文档 ==========
        // 原逻辑（已注释，避免启动时同步调用 qwen3.7-text-embedding）：
        // List<Document> documents = loveAppDocumentLoader.loadMarkdowns();
        // simpleVectorStore.add(documents);
        // 文档加载已迁移到运行期（CommandLineRunner / 首次 RAG 调用时），启动不再阻塞
        return simpleVectorStore;
    }
}
