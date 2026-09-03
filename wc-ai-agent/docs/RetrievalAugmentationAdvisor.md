# RetrievalAugmentationAdvisor 详解

> Spring AI 1.1.x 官方 RAG Advisor，基于 **Modular RAG Architecture**（模块化 RAG 架构）。
> 源码：[`org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor`](https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html)

---

## 一、它解决什么问题？

大语言模型（LLM）有两个先天缺陷：

1. **知识陈旧** —— 训练数据有截止日期，不知道最新事件
2. **不具备私有知识** —— 没学过公司内部文档、个人笔记等

**RAG（Retrieval Augmented Generation，检索增强生成）** 的思路是：

```
┌──────────────────────────────────────────────────────────┐
│  用户提问 → 先从知识库检索相关资料 → 把资料塞给模型 → 模型作答 │
└──────────────────────────────────────────────────────────┘
```

`RetrievalAugmentationAdvisor` 就是 Spring AI 提供的**官方 RAG 实现**，以 **Advisor 形式**无缝嵌入 `ChatClient` 调用链。

---

## 二、整体架构

```
                        ChatClient 调用链
                                │
                                ▼
                ┌───────────────────────────────┐
                │ RetrievalAugmentationAdvisor │
                │         (Modular RAG)        │
                └───────────────────────────────┘
                                │
        ┌──────────┬──────────┬──────────┬──────────┐
        ▼          ▼          ▼          ▼          ▼
   Pre-Retrieval Retrieval  Post-Retrieval  Augmentation
   (查询预处理)   (文档检索)   (文档后处理)   (Prompt 拼装)
```

---

## 三、7 步流水线（Modular RAG 核心）

`RetrievalAugmentationAdvisor#before()` 方法定义了完整的处理流程：

```
用户输入
  │
  ▼
[0] 构造 Query（携带历史、context）
  │
  ▼
[1] QueryTransformer 链 —— 改写 / 标准化问题
  │
  ▼
[2] QueryExpander —— 扩展为多个查询
  │
  ▼
[3] DocumentRetriever —— 并发检索每个查询
  │
  ▼
[4] DocumentJoiner —— 合并多路检索结果
  │
  ▼
[5] DocumentPostProcessor 链 —— 后处理（去重 / 重排 / 压缩）
  │
  ▼
[6] QueryAugmenter —— 拼装最终 Prompt
  │
  ▼
[7] mutate() 更新 ChatClientRequest
  │
  ▼
发送给大模型
```

### 3.1 Step 0：构造 Query

```java
Query originalQuery = Query.builder()
    .text(chatClientRequest.prompt().getUserMessage().getText()) // 用户原文
    .history(chatClientRequest.prompt().getInstructions())         // 历史对话
    .context(context)                                            // 共享 context
    .build();
```

| 项 | 说明 |
|---|---|
| **作用** | 把用户输入、对话历史、context 封装成 `Query` 对象 |
| **关键类** | `org.springframework.ai.rag.Query` |
| **可定制** | ❌ 此步骤为固定逻辑，无扩展点 |

### 3.2 Step 1：QueryTransformer（可选）

```java
Query transformedQuery = originalQuery;
for (var queryTransformer : this.queryTransformers) {
    transformedQuery = queryTransformer.apply(transformedQuery);
}
```

| 项 | 说明 |
|---|---|
| **作用** | 链式改写 Query，如去除口语化、补全上下文、压缩噪声 |
| **关键接口** | `org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer` |
| **示例** | `"我婚后不太亲密怎么办"` → `"如何改善婚后亲密关系"` |
| **默认实现** | 无（不配置则跳过此步骤） |
| **可定制** | ✅ 实现 `QueryTransformer` 接口即可 |

**示例：自定义改写器**

```java
public class MyQueryTransformer implements QueryTransformer {
    @Override
    public Query apply(Query query) {
        String rewritten = query.text().replaceAll("咋办|怎么办", "如何处理");
        return query.mutate().text(rewritten).build();
    }
}
```

### 3.3 Step 2：QueryExpander（可选）

```java
List<Query> expandedQueries = this.queryExpander != null
    ? this.queryExpander.expand(transformedQuery)
    : List.of(transformedQuery);
```

| 项 | 说明 |
|---|---|
| **作用** | 把单个查询扩展为多个查询，多角度检索以提升召回率 |
| **关键接口** | `org.springframework.ai.rag.preretrieval.query.expansion.QueryExpander` |
| **示例** | `"婚后亲密"` → `[原问, "婚后沟通技巧", "亲密关系维护"]` |
| **默认实现** | 无（不配置则只用 1 个查询） |
| **可定制** | ✅ 实现 `QueryExpander` 接口 |

**Spring AI 内置的 MultiQueryExpander**

```java
MultiQueryExpander expander = MultiQueryExpander.builder()
    .chatClient(chatClient) // 用 LLM 自动生成多个变体查询
    .numberOfQueries(5)
    .build();

RetrievalAugmentationAdvisor advisor = RetrievalAugmentationAdvisor.builder()
    .queryExpander(expander)
    .documentRetriever(...)
    .build();
```

### 3.4 Step 3：DocumentRetriever（必填）

```java
Map<Query, List<List<Document>>> documentsForQuery = expandedQueries.stream()
    .map(query -> CompletableFuture.supplyAsync(
        () -> getDocumentsForQuery(query),
        this.taskExecutor))
    .toList()
    .stream()
    .map(CompletableFuture::join)
    .collect(Collectors.toMap(...));
```

| 项 | 说明 |
|---|---|
| **作用** | 根据 Query 从数据源检索相关 Document |
| **关键接口** | `org.springframework.ai.rag.retrieval.search.DocumentRetriever` |
| **默认实现** | **无（必须显式配置）** |
| **并发性** | 多个 Query **异步并发** 执行，默认 4~16 线程池 |
| **可定制** | ✅ |

**内置实现：`VectorStoreDocumentRetriever`**

```java
DocumentRetriever retriever = VectorStoreDocumentRetriever.builder()
    .vectorStore(loveAppVectorStore)  // PG / Redis / ES 等
    .topK(5)                          // 召回 top 5
    .similarityThreshold(0.7)         // 相似度阈值
    .filterExpression(...)            // 元数据过滤
    .build();
```

### 3.5 Step 4：DocumentJoiner（默认）

```java
List<Document> documents = this.documentJoiner.join(documentsForQuery);
```

| 项 | 说明 |
|---|---|
| **作用** | 把多路检索结果（多个 query × 多个数据源）合并、去重、排序 |
| **关键接口** | `org.springframework.ai.rag.retrieval.join.DocumentJoiner` |
| **默认实现** | `ConcatenationDocumentJoiner`（简单拼接） |
| **可定制** | ✅ 实现 `DocumentJoiner` 接口 |

**内置实现对比**

| 实现类 | 策略 |
|---|---|
| `ConcatenationDocumentJoiner` | 直接拼接所有文档（不去重） |
| 自定义 `ReciprocalRankFusionJoiner` | RRF 算法融合排序（业界常用） |

### 3.6 Step 5：DocumentPostProcessor（可选）

```java
for (var documentPostProcessor : this.documentPostProcessors) {
    documents = documentPostProcessor.process(originalQuery, documents);
}
```

| 项 | 说明 |
|---|---|
| **作用** | 对检索到的文档做精加工：去重、rerank、压缩、过滤等 |
| **关键接口** | `org.springframework.ai.rag.postretrieval.document.DocumentPostProcessor` |
| **默认实现** | 无 |
| **可定制** | ✅ |

**典型应用场景**

| 场景 | 处理器 |
|---|---|
| 重排序 | 接 Cohere / BGE Reranker API |
| 文档去重 | 基于内容 hash 或 embedding 相似度 |
| 内容压缩 | 截断超长文档、提取关键句 |
| 时效过滤 | 丢弃 N 天前的过期文档 |

### 3.7 Step 6：QueryAugmenter（默认）

```java
Query augmentedQuery = this.queryAugmenter.augment(originalQuery, documents);
```

| 项 | 说明 |
|---|---|
| **作用** | 把检索到的文档拼装进 Prompt 的 system 消息 |
| **关键接口** | `org.springframework.ai.rag.generation.augmentation.QueryAugmenter` |
| **默认实现** | `ContextualQueryAugmenter` |
| **可定制** | ✅ |

**`ContextualQueryAugmenter` 默认模板**

```
你是 AI 问答助手。请使用提供的 context 信息回答用户问题。
仅使用以下 context 回答问题。如果不知道答案，就直接说不知道。

=====================
context:
[doc1 内容]
[doc2 内容]
[doc3 内容]
=====================

用户问题：{原始问题}
```

### 3.8 Step 7：更新请求

```java
return chatClientRequest.mutate()
    .prompt(chatClientRequest.prompt().augmentUserMessage(augmentedQuery.text()))
    .context(context)
    .build();
```

| 项 | 说明 |
|---|---|
| **作用** | 用增强后的 Prompt 替换原 Prompt，准备发送给大模型 |
| **关键技术** | `mutate()` 不可变更新（Builder 模式） |

---

## 四、核心类与接口全景

### 4.1 类图

```
                       ┌─────────────────────────────┐
                       │ RetrievalAugmentationAdvisor │
                       │      (implements BaseAdvisor) │
                       └──────────────┬───────────────┘
                                      │ contains
        ┌──────────────┬──────────────┼──────────────┬──────────────┐
        ▼              ▼              ▼              ▼              ▼
  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐
  │ Query    │  │ Query    │  │ Document │  │ Document │  │ Query    │
  │Transformer│  │Expander  │  │Retriever │  │Joiner    │  │Augmenter │
  └──────────┘  └──────────┘  └──────────┘  └──────────┘  └──────────┘
                                      │
                                      │ implements
                                      ▼
                              ┌──────────────┐
                              │VectorStore   │
                              │DocumentRetriever │
                              └──────────────┘
```

### 4.2 核心类清单

| 类 / 接口 | 包 | 角色 |
|---|---|---|
| `RetrievalAugmentationAdvisor` | `org.springframework.ai.rag.advisor` | RAG Advisor 主体 |
| `Query` | `org.springframework.ai.rag` | 查询对象（text + history + context） |
| `QueryTransformer` | `...rag.preretrieval.query.transformation` | 查询改写器 |
| `QueryExpander` | `...rag.preretrieval.query.expansion` | 查询扩展器 |
| `DocumentRetriever` | `...rag.retrieval.search` | 文档检索器 |
| `DocumentJoiner` | `...rag.retrieval.join` | 文档合并器 |
| `DocumentPostProcessor` | `...rag.postretrieval.document` | 文档后处理器 |
| `QueryAugmenter` | `...rag.generation.augmentation` | Prompt 增强器 |
| `ContextualQueryAugmenter` | 同上 | 默认 QueryAugmenter 实现 |
| `ConcatenationDocumentJoiner` | `...rag.retrieval.join` | 默认 DocumentJoiner 实现 |
| `VectorStoreDocumentRetriever` | `...rag.retrieval.search` | 常用 DocumentRetriever 实现 |
| `Document` | `org.springframework.ai.document` | 检索结果文档对象 |

---

## 五、Builder API 速查

```java
RetrievalAugmentationAdvisor advisor = RetrievalAugmentationAdvisor.builder()
    // ========== Pre-Retrieval（查询预处理）==========
    .queryTransformers(transformer1, transformer2) // 改写器链
    .queryExpander(myExpander)                    // 查询扩展器
    
    // ========== Retrieval（检索）==========
    .documentRetriever(myRetriever)               // ⭐必填
    
    // ========== Post-Retrieval（后处理）==========
    .documentJoiner(myJoiner)                    // 合并多路结果
    .documentPostProcessors(processor1, processor2) // 后处理链
    
    // ========== Augmentation（Prompt 拼装）==========
    .queryAugmenter(myAugmenter)                  // 自定义拼装模板
    
    // ========== 运行时配置 ==========
    .taskExecutor(myExecutor)                     // 异步执行器
    .scheduler(myScheduler)                       // Reactor 调度器
    .order(0)                                     // Advisor 顺序
    .build();
```

---

## 六、实战示例

### 6.1 最小用法（仅配置 Retriever）

```java
RetrievalAugmentationAdvisor ragAdvisor = RetrievalAugmentationAdvisor.builder()
    .documentRetriever(VectorStoreDocumentRetriever.builder()
        .vectorStore(loveAppVectorStore)
        .topK(5)
        .similarityThreshold(0.7)
        .build())
    .build();

String answer = chatClient.prompt()
    .user("婚后关系不太亲密怎么办？")
    .advisors(ragAdvisor)
    .call()
    .content();
```

### 6.2 进阶用法：扩展 + 后处理 + 自定义模板

```java
RetrievalAugmentationAdvisor ragAdvisor = RetrievalAugmentationAdvisor.builder()
    // 1. 自动扩展为 5 个查询
    .queryExpander(MultiQueryExpander.builder()
        .chatClient(chatClient)
        .numberOfQueries(5)
        .build())
    
    // 2. PG 向量库检索
    .documentRetriever(VectorStoreDocumentRetriever.builder()
        .vectorStore(pgVectorStore)
        .topK(10)  // 召回多一点，供后续 rerank
        .build())
    
    // 3. Rerank 精排
    .documentPostProcessors(cohereRerankProcessor)
    
    // 4. 自定义 Prompt 模板
    .queryAugmenter(ContextualQueryAugmenter.builder()
        .promptTemplate(customPromptTemplate)
        .build())
    .build();
```

### 6.3 自定义 QueryTransformer 示例

```java
public class ContextAwareQueryTransformer implements QueryTransformer {
    @Override
    public Query apply(Query query) {
        String original = query.text();
        // 补全上下文
        String enriched = "在公司内部知识库中，" + original;
        return query.mutate().text(enriched).build();
    }
}

// 使用
RetrievalAugmentationAdvisor advisor = RetrievalAugmentationAdvisor.builder()
    .queryTransformers(new ContextAwareQueryTransformer())
    .documentRetriever(...)
    .build();
```

---

## 七、与 1.0.x 的 `QuestionAnswerAdvisor` 对比

| 维度 | `QuestionAnswerAdvisor` (1.0.x) | `RetrievalAugmentationAdvisor` (1.1.x) |
|---|---|---|
| 设计思路 | 黑盒封装，固定流程 | 模块化架构，6 个可替换组件 |
| 查询改写 | ❌ | ✅ `QueryTransformer` |
| 查询扩展 | ❌ | ✅ `QueryExpander` |
| 多路合并 | ❌ | ✅ `DocumentJoiner` |
| 文档后处理 | ❌ | ✅ `DocumentPostProcessor` |
| Prompt 模板 | 硬编码 | ✅ 可自定义 `QueryAugmenter` |
| 异步检索 | ❌ | ✅ `CompletableFuture` + 线程池 |

**迁移建议**：1.0.x → 1.1.x 时，所有 `QuestionAnswerAdvisor` 都可以用 `RetrievalAugmentationAdvisor.builder().documentRetriever(...).build()` 平替。

---

## 八、注意事项 & 最佳实践

### 8.1 必填参数

只有 `documentRetriever` 是必填的，其他都可以省略（用默认实现）。

```java
// ✅ 合法 ——只配 Retriever
RetrievalAugmentationAdvisor.builder().documentRetriever(...).build();

// ❌ 运行时抛异常
RetrievalAugmentationAdvisor.builder().build();
// IllegalArgumentException: documentRetriever cannot be null
```

### 8.2 topK 与上下文长度

```java
// 文档太多 → 超出模型上下文窗口 → 报错
VectorStoreDocumentRetriever.builder().vectorStore(...).topK(20).build();

// ✅ 解决方案：
// 1. topK 不要太大（建议 3~10）
// 2. 用 DocumentPostProcessor 做 rerank / 截断
// 3. 用 ContextualQueryAugmenter 的 maxContextSize 限制
```

### 8.3 异步执行的线程安全

`RetrievalAugmentationAdvisor` 默认用 `ThreadPoolTaskExecutor`（核心 4，最大 16 线程）并发检索多个 Query。

```java
// 自定义线程池
.taskExecutor(myCustomExecutor)
```

如果 `DocumentRetriever` 实现本身不是线程安全的（如某些老旧客户端），需要谨慎。

### 8.4 Order 与多 Advisor 协作

```java
.advisors(
    loggingAdvisor,                                  // 1. 日志
    RetrievalAugmentationAdvisor.builder()...build(), // 2. RAG 检索
    messageChatMemoryAdvisor                          // 3. 记忆
)
```

`order` 越小越先执行，RAG 通常在前。

### 8.5 自定义 QueryAugmenter 的常见模式

```java
public class PersonaAugmenter implements QueryAugmenter {
    @Override
    public Query augment(Query query, List<Document> documents) {
        String contextText = documents.stream()
            .map(Document::getText)
            .collect(Collectors.joining("\n---\n"));
        
        String newPrompt = """
            你是资深恋爱顾问小爱。请基于以下参考资料回答用户问题。
            参考资料：
            %s
            
            用户问题：%s
            """.formatted(contextText, query.text());
        
        return query.mutate().text(newPrompt).build();
    }
}
```

---

## 九、运行时序图

```
ChatClient.prompt().user(...).advisors(ragAdvisor).call()
  │
  ▼
进入 AdvisorChain
  │
  ▼
ragAdvisor.before(ChatClientRequest)         ← 【前置处理】
  │
  ├─ 构造 Query
  ├─ QueryTransformer 链改写
  ├─ QueryExpander 扩展
  ├─ DocumentRetriever 异步检索（并发）
  ├─ DocumentJoiner 合并
  ├─ DocumentPostProcessor 链处理
  ├─ QueryAugmenter 拼装 Prompt
  └─ mutate() 更新请求
  │
  ▼
[其他 Advisor.before()]                       ← 比如 ChatMemoryAdvisor
  │
  ▼
调用 ChatModel.call()                         ← 真正发给 LLM
  │
  ▼
[其他 Advisor.after()]
  │
  ▼
ragAdvisor.after(ChatClientResponse)          ← 【后置处理】
  │
  └─ 把检索到的文档存入 Response.metadata
  │
  ▼
返回 ChatResponse
  │
  ▼
.content() 提取文本
```

---

## 十、参考文档

- 论文：[Modular RAG: Transforming RAG Systems into LEGO-like Reconfigurable Frameworks](http://export.arxiv.org/abs/2407.21059)
- 论文：[Corrective Retrieval Augmented Generation](https://export.arxiv.org/abs/2401.15884)
- Spring AI 官方文档：[Retrieval Augmented Generation](https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html)
- 项目源码：`org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor`（Spring AI 1.1.8）
