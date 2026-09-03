package com.wc.tools;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

public class TerminalOperationTool
{

    @Tool(description = "Execute a command in the terminal")
    public String executeTerminalCommand(@ToolParam(description = "Command to execute in the terminal") String command)
    {
        StringBuilder output = new StringBuilder();
        try
        {
            System.out.println("当前调用工具为：终端操作");

            // 用 chcp 65001 把 cmd.exe 切到 UTF-8 代码页,避免中文 Windows 默认 GBK 输出导致的乱码
            ProcessBuilder builder = new ProcessBuilder("cmd.exe", "/c", "chcp 65001 > nul && " + command);
            // 合并 stderr 到 stdout,避免错误信息丢失
            builder.redirectErrorStream(true);
            Process process = builder.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)))
            {
                String line;
                while ((line = reader.readLine()) != null)
                {
                    output.append(line).append("\n");
                }
            }
            int exitCode = process.waitFor();
            if (exitCode != 0)
            {
                output.append("Command execution failed with exit code: ").append(exitCode);
            }
        } catch (IOException | InterruptedException e)
        {
            output.append("Error executing command: ").append(e.getMessage());
        }
        return output.toString();
    }
}
