package com.wc.app;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.wc.tools.PDFGenerationTool;

public class PDFGenerationToolTest
{

    @Test
    public void testGeneratePDF()
    {
        PDFGenerationTool tool = new PDFGenerationTool();
        String fileName = "编程导航原创项目.pdf";
        String content = """
                # 上海约会计划

                上午 **外滩源** 游览，中午 *南京东路* 用餐。

                ## 行程表

                | 时间 | 安排 |
                |---|---|
                | 09:00 | 外滩 |
                | 12:00 | 南京东路 |

                > 小贴士：周末人多，建议早出发。

                - 无序列表项 1
                - 无序列表项 2

                1. 有序步骤 1
                2. 有序步骤 2

                ```java
                System.out.println("Hello PDF");
                ```

                ![红点图](data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==)

                ~~删除线~~ [codefather](https://www.codefather.cn)
                """;
        String result = tool.generatePDF(fileName, content);
        assertTrue(result.startsWith("PDF generated successfully"));
    }

    @Test
    public void testGeneratePDFWithNetworkImage()
    {
        PDFGenerationTool tool = new PDFGenerationTool();
        String fileName = "图片嵌入测试.pdf";
        String content = """
                # 图片嵌入测试

                下面是一张真实的网络图片（百度 Logo）：

                ![百度Logo](https://www.baidu.com/img/flexible/logo/pc/result.png)

                下面是一张无效图片，应显示占位符而不是静默丢失：

                ![无效图片](https://invalid.example.com/no-such-image.jpg)
                """;
        String result = tool.generatePDF(fileName, content);
        assertTrue(result.startsWith("PDF generated successfully"));
    }

    /** 复现 run() 端到端时 generatePDF 抛 IndexOutOfBoundsException 的问题 */
    @Test
    public void testReproduceRunFailure()
    {
        PDFGenerationTool tool = new PDFGenerationTool();
        String fileName = "复现测试.pdf";
        String content = """
                ![封面](https://images.pexels.com/photos/30685862/pexels-photo-30685862.jpeg?auto=compress&cs=tinysrgb&h=650&w=940)

                # 静安 · 梧桐与星光

                > **坐标原点**：上海市静安区（静安寺 / 南京西路商圈）

                ---

                ## 一、为什么选静安？

                - **密度极高**：石库门、梧桐小马路
                - **氛围混搭**：前一秒在百年张园拍照

                | # | 地点 | 距静安寺 | 类型 | 花费 | 最佳时段 | 推荐指数 |
                |---|---|---|---|---|---|---|
                | 1 | **张园**（威海路·茂名北路） | 1.6 km | 石库门历史街区 | 免费 | 14:00–17:00 | ★★★★★ |
                | 2 | **丰盛里** | 1.6 km | 餐饮 + 小酒馆街区 | 人均 ¥150–400 | 18:00–22:00 | ★★★★★ |

                > ⚠️ 静安寺为宗教场所，殿内禁止拍照。

                ### 🕙 10:00 — 12:00 ｜ 上海自然博物馆

                - [ ] 确认日期避开周二
                - [ ] 双方身份证

                ```java
                System.out.println("test");
                ```

                ![情侣牵手](https://images.pexels.com/photos/6343599/pexels-photo-6343599.jpeg?auto=compress&cs=tinysrgb&h=650&w=940)

                *祝你们有一个美好的约会 🌃*
                """;
        String result = tool.generatePDF(fileName, content);
        assertTrue(result.startsWith("PDF generated successfully"));
    }

    /** 诊断：看 DefaultFontProvider(false, true, false) 里到底注册了哪些字体 */
    @Test
    public void testFontProviderContents() throws Exception
    {
        var fp = new com.itextpdf.html2pdf.resolver.font.DefaultFontProvider(false, true, false);
        System.out.println("===== shipped fonts =====");
        System.out.println("total: " + fp.getFontSet().getFonts().size());
        fp.getFontSet().getFonts().forEach(f -> System.out.println("FONT: " + f));
        // 显式注册 STSong 后再确认
        fp.addFont(com.itextpdf.io.font.FontProgramFactory.createFont("STSongStd-Light"));
        System.out.println("===== after explicit STSong =====");
        System.out.println("total: " + fp.getFontSet().getFonts().size());
        fp.getFontSet().getFonts().forEach(f -> System.out.println("FONT: " + f));
    }

    /** 端到端验证：生成的中文 PDF 里必须真的嵌入并使用了中文字体（SimHei） */
    @Test
    public void testGeneratedPdfEmbedsCJKFont() throws Exception
    {
        PDFGenerationTool tool = new PDFGenerationTool();
        String fileName = "中文字体验证.pdf";
        String result = tool.generatePDF(fileName, "# 中文字体测试\n\n这段话必须能正常显示中文。");
        assertTrue(result.startsWith("PDF generated successfully"));

        String filePath = System.getProperty("user.dir") + "/tmp/pdf/" + fileName;
        // 字体字典 /BaseFont /SimHei 以明文写在 PDF 对象里，直接按字节搜索
        String raw = new String(java.nio.file.Files.readAllBytes(java.nio.file.Path.of(filePath)),
                java.nio.charset.StandardCharsets.ISO_8859_1);
        assertTrue(raw.contains("SimHei"), "PDF 中未找到 SimHei 字体，中文会渲染为方框");
    }
}
