package com.wc.tools;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import com.itextpdf.html2pdf.ConverterProperties;
import com.itextpdf.html2pdf.HtmlConverter;
import com.itextpdf.html2pdf.resolver.font.DefaultFontProvider;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.wc.constant.FileConstant;

import cn.hutool.core.io.FileUtil;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
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

    // HTML <img> 标签的 src 提取
    private static final Pattern HTML_IMG_SRC = Pattern.compile("<img\\b[^>]*\\bsrc=\"([^\"]+)\"",
            Pattern.CASE_INSENSITIVE);

    // 伪装成浏览器请求，绕过部分图站的 Referer/UA 防盗链
    private static final String BROWSER_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    @Tool(description = "Generate a PDF file from Markdown content. "
            + "Fully supports Markdown syntax: headings (#), bold (**), italic (*), strikethrough (~~), "
            + "ordered/unordered lists, tables, code blocks, blockquotes, links, and inline images. "
            + "Images can use HTTP/HTTPS URLs, local file paths, or data:image/...;base64,... URIs. "
            + "Images are downloaded and embedded into the PDF automatically (WebP is converted to PNG). "
            + "If an image cannot be loaded, a visible placeholder is inserted and the PDF is still generated.")
    public String generatePDF(@ToolParam(description = "Name of the file to save the generated PDF") String fileName,
            @ToolParam(description = "Markdown content to render into the PDF") String content) {
        System.out.println("当前调用工具为：PDF生成");
        String fileDir = FileConstant.FILE_SAVE_DIR + "/pdf";
        String filePath = fileDir + "/" + fileName;
        try {
            FileUtil.mkdir(fileDir);

            // 用字面占位符替换而非 String.formatted：CSS 里的 % 会被 formatted 当格式符解析
            // embedImages：把 HTML 里的图片统一预取并内嵌为 data URI，避免 pdfHTML 静默丢弃加载失败的图片
            String html = embedImages(HTML_TEMPLATE.replace("%BODY%", mdToHtml(content)));
            ConverterProperties props = new ConverterProperties();
            // 不注册内置的 Helvetica 等西文字体，只注册 font-asian 提供的
            // STSong 等中日韩字体和操作系统字体，保证中文正常显示
            // 只注册 font-asian 自带的 STSong 等字体，不注册系统字体：
            // Windows 的 Segoe UI Emoji 等字体的 GPOS 表会触发
            // iText 9.2.0 的解析 bug（IndexOutOfBoundsException）导致整个转换崩溃
            DefaultFontProvider fontProvider = new DefaultFontProvider(false, true, false);
            // 注册黑体 SimHei：普通单文件 TTF、自带 cmap，字体选择器能正确识别其
            // Unicode 覆盖范围。
            // 两个已踩过的坑：
            // 1. 不能全量注册系统字体——Segoe UI Emoji 的 GPOS 表会触发
            //    iText 9.2.0 解析 bug（IndexOutOfBoundsException）导致整个转换崩溃；
            // 2. 不能用 CID 字体 STSong + UniGB-UCS2-H 编码注册——选择器创建
            //    PdfFont 会失败（temptFont null NPE）。
            String simheiPath = System.getenv("WINDIR") + "/Fonts/simhei.ttf";
            File simhei = new File(simheiPath);
            if (simhei.exists()) {
                fontProvider.addFont(simheiPath);
            } else {
                log.warn("SimHei font not found at {}, Chinese text may render as boxes", simheiPath);
            }
            props.setFontProvider(fontProvider);
            try (PdfWriter writer = new PdfWriter(filePath)) {
                HtmlConverter.convertToPdf(html, writer, props);
            }
            return "PDF generated successfully to: " + filePath;
        } catch (Exception e) {
            // 捕获所有异常（含 RuntimeException），完整堆栈进日志，
            // 给 agent 返回简洁的错误信息，避免异常穿透到调用方
            log.error("PDF generation failed", e);
            return "Error generating PDF: " + e;
        }
    }

    /**
     * 用 flexmark 把 Markdown 转成 HTML 片段。
     * 开启表格、删除线、自动链接扩展，覆盖 GFM 常用语法。
     */
    private String mdToHtml(String md) {
        var options = new com.vladsch.flexmark.util.data.MutableDataSet();
        options.set(com.vladsch.flexmark.html.HtmlRenderer.SOFT_BREAK, "<br />\n");
        List<com.vladsch.flexmark.util.misc.Extension> extensions = List.of(
                com.vladsch.flexmark.ext.tables.TablesExtension.create(),
                com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension.create(),
                com.vladsch.flexmark.ext.autolink.AutolinkExtension.create());
        options.set(com.vladsch.flexmark.parser.Parser.EXTENSIONS, extensions);

        var parser = com.vladsch.flexmark.parser.Parser.builder(options).build();
        var document = parser.parse(md);
        return com.vladsch.flexmark.html.HtmlRenderer.builder(options).build().render(document);
    }

    /**
     * 把 HTML 中所有 &lt;img&gt; 的 src 预取并替换为 data URI 内嵌进 PDF。
     * 支持网络图片（带浏览器请求头绕过防盗链）、本地文件路径、data URI；
     * WebP 自动转 PNG（iText 不支持 WebP）。
     * 加载失败或格式不支持的图片替换为可见占位文本，并在日志中记录原因，
     * 不再像 pdfHTML 默认行为那样静默丢弃。
     */
    private String embedImages(String html) {
        Matcher matcher = HTML_IMG_SRC.matcher(html);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String src = matcher.group(1);
            String replacement = "[图片加载失败: " + src + "]";
            byte[] bytes = loadImageBytes(src);
            if (bytes != null) {
                String mime = detectImageMime(bytes);
                if ("image/webp".equals(mime)) {
                    byte[] png = webpToPng(bytes);
                    if (png != null) {
                        bytes = png;
                        mime = "image/png";
                    } else {
                        mime = null;
                    }
                }
                if (mime != null) {
                    String dataUri = "data:" + mime + ";base64,"
                            + Base64.getEncoder().encodeToString(bytes);
                    replacement = matcher.group(0).replace(src, dataUri);
                } else {
                    replacement = "[暂不支持的图片格式: " + src + "]";
                    log.warn("Unsupported image format, placeholder inserted: {}", src);
                }
            } else {
                log.warn("Image load failed, placeholder inserted: {}", src);
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * 读取图片字节。支持 data URI、HTTP/HTTPS（带浏览器请求头）和本地文件路径。
     * 返回 null 表示加载失败。
     */
    private byte[] loadImageBytes(String src) {
        try {
            if (src.startsWith("data:")) {
                int comma = src.indexOf(',');
                return comma < 0 ? null : Base64.getDecoder().decode(src.substring(comma + 1));
            }
            if (src.startsWith("http://") || src.startsWith("https://")) {
                try (HttpResponse response = HttpUtil.createGet(src)
                        .header("User-Agent", BROWSER_UA)
                        .timeout(10_000)
                        .execute()) {
                    if (!response.isOk()) {
                        log.warn("Image HTTP status {}: {}", response.getStatus(), src);
                        return null;
                    }
                    byte[] bytes = response.bodyBytes();
                    return detectImageMime(bytes) != null ? bytes : null;
                }
            }
            // 本地路径：先按原样找，找不到再尝试相对 FILE_SAVE_DIR 解析
            File localFile = FileUtil.file(src);
            if (!localFile.isAbsolute() && !localFile.exists()) {
                File underSaveDir = FileUtil.file(FileConstant.FILE_SAVE_DIR, src);
                if (underSaveDir.exists()) {
                    localFile = underSaveDir;
                }
            }
            if (localFile.exists()) {
                return FileUtil.readBytes(localFile);
            }
            log.warn("Image file not found: {}", src);
            return null;
        } catch (Exception e) {
            log.warn("Image load error: {} ({})", src, e.getMessage());
            return null;
        }
    }

    /** 通过魔数识别图片格式，未识别返回 null */
    private String detectImageMime(byte[] bytes) {
        if (bytes == null || bytes.length < 12) {
            return null;
        }
        if ((bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P') {
            return "image/png";
        }
        if ((bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8) {
            return "image/jpeg";
        }
        if (bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F') {
            return "image/gif";
        }
        if (bytes[0] == 'B' && bytes[1] == 'M') {
            return "image/bmp";
        }
        if (bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            return "image/webp";
        }
        return null;
    }

    /** WebP 解码后转 PNG（依赖 webp-imageio 的 ImageIO 插件），失败返回 null */
    private byte[] webpToPng(byte[] webp) {
        try {
            var image = ImageIO.read(new ByteArrayInputStream(webp));
            if (image == null) {
                return null;
            }
            var out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (Exception e) {
            log.warn("WebP decode failed: {}", e.getMessage());
            return null;
        }
    }
}
