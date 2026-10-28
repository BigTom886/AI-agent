package com.wc.rag;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgDistanceType.COSINE_DISTANCE;
import static org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgIndexType.HNSW;

/**
 * pgvector 向量库 —— RAG 检索的知识库存储。
 *
 * <p>
 * 知识库（恋爱知识库：热恋篇 / 恋爱篇 / 分手篇，共 15 个片段）已经向量化后存在
 * PostgreSQL 的 {@code vector_store} 表里，本类只负责把这个表接进 Spring AI：
 * 查询时把用户问题向量化，用余弦相似度在这个表里找最相近的片段。
 *
 * <p>
 * 依赖：数据库装了 {@code vector} 扩展（本地库已有 0.8.2），并且有
 * {@link EmbeddingModel} —— 提问和问题必须用同一个模型向量化，否则向量空间对不上，
 * 检索结果全是噪声。
 *
 * <p>
 * 表结构由框架自动创建（{@code initializeSchema(true)}，CREATE TABLE IF NOT EXISTS，
 * 重复启动安全）：id uuid / content text / metadata json / embedding vector(1024)。
 */
@Configuration
public class PgVectorVectorStoreConfig {

    @Bean
    public VectorStore pgVectorVectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {
        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
                // 与知识库已有向量维度一致（text-embedding-v3 输出 1024 维）
                .dimensions(1024)
                .distanceType(COSINE_DISTANCE) // 余弦相似度
                .indexType(HNSW) // HNSW 索引
                .initializeSchema(true) // 表不存在就建，已存在则跳过
                .schemaName("public")
                .vectorTableName("vector_store")
                .build();
    }
}
