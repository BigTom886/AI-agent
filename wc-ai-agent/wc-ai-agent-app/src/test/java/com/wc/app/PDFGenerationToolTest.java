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
}
