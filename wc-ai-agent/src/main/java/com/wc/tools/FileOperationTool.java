package com.wc.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import com.wc.constant.FileConstant;

import cn.hutool.core.io.FileUtil;

public class FileOperationTool
{

    private final String FILE_DIR = FileConstant.FILE_SAVE_DIR + "/file";

    @Tool(description = "Read content from a file")
    public String readFile(@ToolParam(description = "Name of the file to read") String fileName)
    {
        System.out.println("当前调用工具为：文件操作");
        String filePath = FILE_DIR + "/" + fileName;
        try
        {
            return FileUtil.readUtf8String(filePath);
        } catch (Exception e)
        {
            return "Error reading file: " + e.getMessage();
        }
    }

    @Tool(description = "Write content to a file")
    public String writeFile(@ToolParam(description = "Name of the file to write") String fileName,
            @ToolParam(description = "Content to write to the file") String content)
    {
        String filePath = FILE_DIR + "/" + fileName;
        try
        {
            // 创建目录
            FileUtil.mkdir(FILE_DIR);
            FileUtil.writeUtf8String(content, filePath);
            return "File written successfully to: " + filePath;
        } catch (Exception e)
        {
            return "Error writing to file: " + e.getMessage();
        }
    }
}
