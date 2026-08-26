package com.wc.rag;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Markdown 文档加载器。
 *
 * <p>
 * 负责从 classpath:document/ 目录下扫描所有 .md 文件， 使用 Spring AI 提供的
 * {@link MarkdownDocumentReader} 将每个 Markdown 文件解析为 一个或多个 {@link Document}
 * 对象，供后续 RAG（检索增强生成）流程使用。
 *
 * <p>
 * 作为 Spring 组件，会在应用启动时由容器自动创建并注入所需的依赖。
 *
 * @author wc
 */
@Component
@Slf4j
public class LoveAppDocumentLoader {

    /**
     * Spring 资源模式解析器，用于按 Ant 风格路径（例如 "classpath:document/*.md"） 批量加载资源文件。由 Spring
     * 容器自动注入。
     */
    private final ResourcePatternResolver resourcePatternResolver;

    /**
     * 构造方法，由 Spring 容器调用以注入资源解析器。
     *
     * @param resourcePatternResolver Spring 提供的资源模式解析器
     */
    LoveAppDocumentLoader(ResourcePatternResolver resourcePatternResolver) {
        this.resourcePatternResolver = resourcePatternResolver;
    }

    /**
     * 加载并解析 classpath:document/ 目录下所有的 Markdown 文件。
     *
     * <p>
     * 处理流程：
     * <ol>
     * <li>通过资源解析器按路径模式匹配所有 Markdown 文件</li>
     * <li>为每个文件构造读取配置（水平分割规则、附加元数据等）</li>
     * <li>使用 {@link MarkdownDocumentReader} 解析文件得到 {@link Document} 列表</li>
     * <li>汇总所有 Document 后返回</li>
     * </ol>
     *
     * @return 解析得到的全部 Document 列表；若加载失败则返回空列表
     */
    public List<Document> loadMarkdowns() {
        // 用于汇总所有 Markdown 文件解析出的 Document 对象
        List<Document> allDocuments = new ArrayList<>();
        try {
            // 按 Ant 路径模式加载 classpath:document/ 下的所有 .md 文件
            // 如需加载其他位置，可修改该路径模式
            Resource[] resources = resourcePatternResolver.getResources("classpath:document/*.md");
            // 遍历每一个 Markdown 资源文件
            for (Resource resource : resources) {
                // 获取文件名，作为元数据写入 Document，便于后续溯源
                String fileName = resource.getFilename();
                // 构造 Markdown 读取配置：
                // - withHorizontalRuleCreateDocument(true)：遇到 --- 水平分割线时切分文档
                // - withIncludeCodeBlock(false)：不把代码块作为独立 Document(就是不提取)
                // - withIncludeBlockquote(false)：不把引用块作为独立 Document(就是不提取)
                // - withAdditionalMetadata("filename", fileName)：追加 filename 元数据

                // 提前文档倒数第3和第2个字作为标签
                String status = fileName.substring(fileName.length() - 6, fileName.length() - 4);

                MarkdownDocumentReaderConfig config = MarkdownDocumentReaderConfig.builder()
                        .withHorizontalRuleCreateDocument(true).withIncludeCodeBlock(false).withIncludeBlockquote(false)
                        .withAdditionalMetadata("filename", fileName).withAdditionalMetadata("status", status).build();
                // 使用配置好的 Reader 解析当前 Markdown 资源
                MarkdownDocumentReader reader = new MarkdownDocumentReader(resource, config);
                // 将解析得到的所有 Document 合并到结果列表
                allDocuments.addAll(reader.get());
                log.info("====={}", reader);
            }
        } catch (IOException e) {
            // 资源加载或读取过程中出现 I/O 异常时，记录错误日志并返回已解析的部分（可能为空）
            log.error("Markdown 文档加载失败", e);
        }
        return allDocuments;
    }
}