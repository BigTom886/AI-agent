package com.wc.app;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

import com.wc.tools.PDFGenerationTool;

public class PDFGenerationToolTest
{

    @Test
    public void testGeneratePDF()
    {
        PDFGenerationTool tool = new PDFGenerationTool();
        String fileName = "编程导航原创项目.pdf";
        String content = "编程导航原创项目 https://www.codefather.cn";
        String result = tool.generatePDF(fileName, content);
        assertNotNull(result);
    }
}
