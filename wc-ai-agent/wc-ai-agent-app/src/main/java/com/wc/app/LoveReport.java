package com.wc.app;

import java.util.List;

/**
 * 恋爱报告 —— 基于「当前会话的对话 + 跨会话用户画像」生成的结构化分析结果
 *
 * 单独抽成一个文件（而不是塞在 LoveApp 里当内部类），
 * 是因为 Controller 在 com.wc.controller 包里要用它作为返回值，
 * 内部类默认是包级可见，跨包访问不到。
 *
 * @param title       报告标题（模型输出的第一行）
 * @param suggestions 建议列表（模型输出的其余行，每行一条）
 */
public record LoveReport(String title, List<String> suggestions)
{
}
