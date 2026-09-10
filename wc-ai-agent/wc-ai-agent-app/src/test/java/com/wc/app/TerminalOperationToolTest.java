package com.wc.app;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.wc.tools.TerminalOperationTool;

public class TerminalOperationToolTest
{
    @TempDir
    Path tempDir;

    @Test
    public void testExecuteTerminalCommand()
    {
        TerminalOperationTool tool = new TerminalOperationTool();
        // 使用 cmd.exe 内置命令,避免依赖外部 PATH(如 Git Bash 提供的 ls)
        String command = "dir";
        String result = tool.executeTerminalCommand(command);
        System.out.println("Terminal command output: " + result);
        // 基础断言:返回非 null
        assertNotNull(result);
        // 强断言:真正执行了命令并产生预期输出
        assertTrue(result.contains("pom.xml"), "执行结果应包含 pom.xml,实际: " + result);
        // 强断言:命令未报错退出
        assertFalse(result.contains("Error executing command"),
                "执行结果不应包含错误信息,实际: " + result);
        assertFalse(result.contains("Command execution failed with exit code"),
                "执行结果不应包含退出码错误,实际: " + result);
    }

    @Test
    public void testChineseOutputNotGarbled() throws IOException
    {
        // 中文 Windows 上 cmd 内置命令输出编码与 chcp 报告常不一致,曾导致乱码。
        // 用确定的中文名文件验证 dir 输出解码正确
        Files.createFile(tempDir.resolve("中文文件测试.txt"));
        TerminalOperationTool tool = new TerminalOperationTool();
        String result = tool.executeTerminalCommand("dir /b \"" + tempDir + "\"");
        assertTrue(result.contains("中文文件测试.txt"),
                "输出应包含正确的中文文件名,实际: " + result);
    }
}
