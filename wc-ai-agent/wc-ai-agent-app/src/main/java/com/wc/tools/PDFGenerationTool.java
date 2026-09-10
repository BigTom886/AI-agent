package com.wc.tools;

import java.io.IOException;
import java.util.List;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import com.itextpdf.html2pdf.ConverterProperties;
import com.itextpdf.html2pdf.HtmlConverter;
import com.itextpdf.html2pdf.resolver.font.DefaultFontProvider;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.wc.constant.FileConstant;

import cn.hutool.core.io.FileUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * PDF 生成工具：Markdown -> HTML（flexmark）-> PDF（iText pdfHTML）。
 * <p>
 * 相比手写正则解析，flexmark 完整支持 Markdown 语法（标题、粗体、斜体、
 * 表格、代码块、引用、链接、图片、删除线、自动链接等），
 * pdfHTML 负责把 HTML 按内置 CSS 渲染成排版良好的 PDF，并自动下载网络图片。
 */
@Slf4j
public class PDFGenerationTool {

    /** 页边距、正文字号等全局排版样式 */
    private static final String HTML_TEMPLATE = """
            <!DOCTYPE html>
            <html>
            <head>
            <style>
              @page { margin: 36pt; }
              body {
                font-family: "STSong", "Microsoft YaHei", sans-serif;
                font-size: 11pt;
                line-height: 1.6;
                color: #222;
              }
              h1 { font-size: 20pt; margin: 12pt 0 8pt; }
              h2 { font-size: 16pt; margin: 10pt 0 6pt; }
              h3 { font-size: 14pt; margin: 8pt 0 4pt; }
              h4, h5, h6 { font-size: 12pt; margin: 8pt 0 4pt; }
              table { border-collapse: collapse; width: 100%; margin: 8pt 0; }
              th, td { border: 0.5pt solid #999; padding: 4pt 6pt; }
              th { background-color: #f0f0f0; }
              code { background-color: #f5f5f5; font-size: 9.5pt; padding: 1pt 3pt; }
              pre { background-color: #f5f5f5; padding: 8pt; font-size: 9.5pt; }
              blockquote { color: #555; border-left: 3pt solid #ccc; margin-left: 0; padding-left: 10pt; }
              img { max-width: 480px; }
              hr { border: none; border-top: 0.5pt solid #ccc; }
              a { color: #1a6ecc; }
            </style>
            </head>
            <body>%BODY%</body>
            <html>
            """;

    @Tool(description = "Generate a PDF file from Markdown content. "
            + "Fully supports Markdown syntax: headings (#), bold (**), italic (*), strikethrough (~~), "
            + "ordered/unordered lists, tables, code blocks, blockquotes, links, and inline images. "
            + "Images can use HTTP/HTTPS URLs, local file paths, or data:image/...;base64,... URIs. "
            + "If an image cannot be loaded, it is skipped and the PDF is still generated.")
    public String generatePDF(@ToolParam(description = "Name of the file to save the generated PDF") String fileName,
            @ToolParam(description = "Markdown content to render into the PDF") String content) {
        System.out.println("当前调用工具为：PDF生成");
        String fileDir = FileConstant.FILE_SAVE_DIR + "/pdf";
        String filePath = fileDir + "/" + fileName;
        try {
            FileUtil.mkdir(fileDir);

            // 用字面占位符替换而非 String.formatted：CSS 里的 % 会被 formatted 当格式符解析
            String html = HTML_TEMPLATE.replace("%BODY%", mdToHtml(content));
            ConverterProperties props = new ConverterProperties();
            // 不注册内置的 Helvetica 等西文字体，只注册 font-asian 提供的
            // STSong 等中日韩字体和操作系统字体，保证中文正常显示
            props.setFontProvider(new DefaultFontProvider(false, true, true));
            try (PdfWriter writer = new PdfWriter(filePath)) {
                HtmlConverter.convertToPdf(html, writer, props);
            }
            return "PDF generated successfully to: " + filePath;
        } catch (IOException e) {
            log.error("PDF generation failed: {}", e.getMessage());
            return "Error generating PDF: " + e.getMessage();
        }
    }

    /**
     * 用 flexmark 把 Markdown 转成 HTML 片段。
     * 开启表格、删除线、自动链接扩展，覆盖 GFM 常用语法。
     */
    private String mdToHtml(String md) {
        var options = new com.vladsch.flexmark.util.data.MutableDataSet();
        options.set(com.vladsch.flexmark.html.HtmlRenderer.SOFT_BREAK, "<br />\\n");
        List<com.vladsch.flexmark.util.misc.Extension> extensions = List.of(
                com.vladsch.flexmark.ext.tables.TablesExtension.create(),
                com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension.create(),
                com.vladsch.flexmark.ext.autolink.AutolinkExtension.create());
        options.set(com.vladsch.flexmark.parser.Parser.EXTENSIONS, extensions);

        var parser = com.vladsch.flexmark.parser.Parser.builder(options).build();
        var document = parser.parse(md);
        return com.vladsch.flexmark.html.HtmlRenderer.builder(options).build().render(document);
    }
}
