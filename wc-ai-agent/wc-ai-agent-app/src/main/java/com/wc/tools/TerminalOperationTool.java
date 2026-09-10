package com.wc.tools;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

public class TerminalOperationTool {

    private static final boolean IS_WINDOWS =
            System.getProperty("os.name").toLowerCase().startsWith("windows");

    /**
     * 执行终端命令，Windows 下解决中文乱码。
     *
     * <p>乱码根因：cmd 内置命令（dir/findstr/echo 等）的输出编码跟随系统 OEM 代码页
     * （中文 Windows = GBK/936），与 chcp 报告的控制台代码页经常不一致，
     * "探测 chcp => 猜编码"的方式在中文 Windows 上不可靠。
     *
     * <p>编码策略：
     * <ul>
     *   <li><b>Windows</b>：用 {@code cmd.exe /u /c} 启动，/u 强制 cmd 内置命令对
     *       管道/重定向输出统一使用 UTF-16LE，与代码页无关。</ul>
     *   <li>外部程序（java/git 等）不经过 cmd 转码，按自身编码输出：
     *       无 NUL 字节，先按 UTF-8 严格解码，失败再按系统 OEM 代码页（中文 Windows = GBK）。</li>
     *   <li>判别依据：UTF-16LE 输出必含 NUL 字节（每行 CRLF 即 2 个），
     *       GBK/UTF-8 文本不含 NUL。</li>
     * </ul>
     */
    @Tool(description = "Execute a command in the terminal")
    public String executeTerminalCommand(@ToolParam(description = "Command to execute in the terminal") String command) {
        StringBuilder output = new StringBuilder();
        try {
            System.out.println("当前调用工具为：终端操作");

            ProcessBuilder builder;
            if (IS_WINDOWS) {
                builder = new ProcessBuilder("cmd.exe", "/u", "/c", command);
            } else {
                builder = new ProcessBuilder("/bin/sh", "-c", command);
            }
            builder.redirectErrorStream(true);
            Process process = builder.start();
            output.append(readOutput(process));
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                output.append("Command execution failed with exit code: ").append(exitCode);
            }
        } catch (IOException | InterruptedException e) {
            output.append("Error executing command: ").append(e.getMessage());
        }
        return output.toString();
    }

    private String readOutput(Process process) throws IOException {
        byte[] raw = process.getInputStream().readAllBytes();
        if (raw.length == 0) {
            return "";
        }
        if (!IS_WINDOWS) {
            return new String(raw, StandardCharsets.UTF_8);
        }

        boolean hasNul = false;
        for (byte b : raw) {
            if (b == 0) {
                hasNul = true;
                break;
            }
        }
        if (hasNul) {
            // cmd /u 内置命令输出：UTF-16LE（可能带 BOM，去掉）
            String s = new String(raw, StandardCharsets.UTF_16LE);
            return s.startsWith("\uFEFF") ? s.substring(1) : s;
        }
        // 外部程序输出：先按 UTF-8 严格解码，失败则按系统 OEM 代码页
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(raw)).toString();
        } catch (CharacterCodingException e) {
            return new String(raw, oemCharset());
        }
    }

    private static Charset oemCharset() {
        // sun.jnu.encoding 是 JVM 使用的平台编码（中文 Windows = GBK）
        String encoding = System.getProperty("sun.jnu.encoding");
        if (encoding != null) {
            try {
                return Charset.forName(encoding);
            } catch (Exception ignored) {
            }
        }
        return Charset.forName("GBK");
    }
}
