package com.wc.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import com.wc.constant.FileConstant;

import cn.hutool.core.io.FileUtil;
import cn.hutool.http.HttpUtil;

public class ResourceDownloadTool
{

    @Tool(description = "Download a resource from a given URL")
    public String downloadResource(@ToolParam(description = "URL of the resource to download") String url,
            @ToolParam(description = "Name of the file to save the downloaded resource") String fileName)
    {
        String fileDir = FileConstant.FILE_SAVE_DIR + "/download";
        String filePath = fileDir + "/" + fileName;
        try
        {
            System.out.println("当前调用工具为：资源下载");
            // 创建目录
            FileUtil.mkdir(fileDir);
            // 使用 Hutool 的 downloadFile 方法下载资源
            HttpUtil.downloadFile(url, new String(filePath));
            return "Resource downloaded successfully to: " + filePath;
        } catch (Exception e)
        {
            return "Error downloading resource: " + e.getMessage();
        }
    }
}
