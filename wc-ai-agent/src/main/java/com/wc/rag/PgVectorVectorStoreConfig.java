package com.wc.rag;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgDistanceType.COSINE_DISTANCE;
import static org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgIndexType.HNSW;

@Configuration
public class PgVectorVectorStoreConfig {

    @Bean
    @Primary
    // 效果：所有"按类型找 VectorStore"的注入都会优先拿你自定义的这个，启动器的那个变成备胎。
    // @Resource VectorStore pgVectorVectorStore（按名字）也照样能拿到
    public VectorStore pgVectorVectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel dashscopeEmbeddingModel) {
        VectorStore vectorStore = PgVectorStore.builder(jdbcTemplate, dashscopeEmbeddingModel)
                .dimensions(1024) // 与 qwen3.7-text-embedding 模型输出维度一致
                .distanceType(COSINE_DISTANCE) // Optional: defaults to COSINE_DISTANCE
                .indexType(HNSW) // Optional: defaults to HNSW
                .initializeSchema(true) // Optional: defaults to false
                .schemaName("public") // Optional: defaults to "public"
                .vectorTableName("vector_store") // Optional: defaults to "vector_store"
                .maxDocumentBatchSize(10000) // Optional: defaults to 10000
                .build();
        return vectorStore;
    }
}
