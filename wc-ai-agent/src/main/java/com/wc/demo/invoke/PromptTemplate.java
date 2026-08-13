package com.wc.demo.invoke;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplateActions;
import org.springframework.ai.chat.prompt.PromptTemplateMessageActions;
import org.springframework.ai.content.Media;

/**
 * 自定义 Prompt 模板 —— Spring AI 提示词模板的手工实现。
 *
 * <p>
 * 支持以 <code>{placeholder}</code> 形式的占位符（与 Spring AI 内置
 * {@code NoOpTemplateRenderer} 行为一致），可用于：
 * <ul>
 * <li>直接渲染为提示字符串（{@link #render()} / {@link #render(Map)}）</li>
 * <li>封装为 Spring AI 的 {@link Message}（{@link #createMessage()} 系列）</li>
 * <li>组装成可下发到 ChatModel 的 {@link Prompt}（{@link #create()} 系列）</li>
 * </ul>
 *
 * <p>
 * 典型用法：
 *
 * <pre>{@code
 * PromptTemplate tpl = new PromptTemplate("你好 {name}，请帮我 {task}");
 * tpl.addVariable("name", "王城");
 * Prompt prompt = tpl.create();
 * String reply = chatModel.call(prompt).getResult().getOutput().getText();
 * }</pre>
 *
 * @author wc
 * @since 2026-08-06
 */
public class PromptTemplate implements PromptTemplateActions, PromptTemplateMessageActions
{

    /** 占位符匹配：{varName}。变量名可包含字母、数字、下划线、点号。 */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z_][A-Za-z0-9_.]*)\\}");

    /** 模板原文，含 {placeholder} 占位符。 */
    private String template;

    /** 模板内置变量，render() / createMessage() 默认使用这些变量渲染。 */
    private Map<String, Object> variables;

    public PromptTemplate()
    {
        this.variables = new HashMap<>();
    }

    public PromptTemplate(String template)
    {
        this.template = template;
        this.variables = new HashMap<>();
    }

    public PromptTemplate(String template, Map<String, Object> variables)
    {
        this.template = template;
        this.variables = (variables != null) ? new HashMap<>(variables) : new HashMap<>();
    }

    // ============================ Getter / Setter ============================

    public String getTemplate()
    {
        return template;
    }

    public void setTemplate(String template)
    {
        this.template = template;
    }

    public Map<String, Object> getVariables()
    {
        return variables;
    }

    public void setVariables(Map<String, Object> variables)
    {
        this.variables = (variables != null) ? new HashMap<>(variables) : new HashMap<>();
    }

    /**
     * 向内置变量集合添加单个变量。
     *
     * @param name 占位符名称（不含 {}）
     * @param value 占位符替换值
     */
    public void addVariable(String name, Object value)
    {
        if (this.variables == null)
        {
            this.variables = new HashMap<>();
        }
        this.variables.put(name, value);
    }

    // ============================ 渲染 ============================

    /**
     * 用模板内部已绑定的变量渲染出最终的提示字符串。
     *
     * @return 渲染完成、可直接发送给模型的提示字符串
     */
    @Override
    public String render()
    {
        return render(this.variables);
    }

    /**
     * 用调用方传入的变量集合渲染模板；调用方变量优先级高于内置变量。
     *
     * @param model 占位符变量集合
     * @return 渲染后的提示字符串
     */
    @Override
    public String render(Map<String, Object> model)
    {
        if (this.template == null || this.template.isEmpty())
        {
            return "";
        }
        // 合并变量：内置变量为底，调用方变量覆盖之
        Map<String, Object> merged = new HashMap<>();
        if (this.variables != null)
        {
            merged.putAll(this.variables);
        }
        if (model != null)
        {
            merged.putAll(model);
        }

        Matcher matcher = PLACEHOLDER.matcher(this.template);
        StringBuilder sb = new StringBuilder();
        while (matcher.find())
        {
            String key = matcher.group(1);
            Object value = merged.get(key);
            // 没找到变量时保留原占位符，避免静默吞掉
            String replacement = (value != null) ? Matcher.quoteReplacement(value.toString()) : matcher.group(0);
            matcher.appendReplacement(sb, replacement);
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    // ============================ Message 创建 ============================

    /**
     * 基于内置变量渲染并创建 {@link UserMessage}。
     */
    @Override
    public Message createMessage()
    {
        return UserMessage.builder().text(render()).build();
    }

    /**
     * 基于内置变量渲染并创建带有多媒体附件的 {@link UserMessage}。
     *
     * @param mediaList 附加到消息上的媒体列表（图片、音频、视频等）
     */
    @Override
    public Message createMessage(List<Media> mediaList)
    {
        return UserMessage.builder().text(render()).media(mediaList).build();
    }

    /**
     * 用调用方传入的变量渲染并创建 {@link UserMessage}。
     */
    @Override
    public Message createMessage(Map<String, Object> model)
    {
        return UserMessage.builder().text(render(model)).build();
    }

    // ============================ Prompt 创建 ============================

    /**
     * 用内置变量 + 默认 ChatOptions 构造 {@link Prompt}。
     */
    @Override
    public Prompt create()
    {
        return new Prompt(createMessage());
    }

    /**
     * 用内置变量 + 指定 ChatOptions 构造 {@link Prompt}。
     */
    @Override
    public Prompt create(ChatOptions modelOptions)
    {
        return new Prompt(createMessage(), modelOptions);
    }

    /**
     * 用调用方变量 + 默认 ChatOptions 构造 {@link Prompt}。
     */
    @Override
    public Prompt create(Map<String, Object> model)
    {
        return new Prompt(createMessage(model));
    }

    /**
     * 用调用方变量 + 指定 ChatOptions 构造 {@link Prompt}（最常用形态）。
     */
    @Override
    public Prompt create(Map<String, Object> model, ChatOptions modelOptions)
    {
        return new Prompt(createMessage(model), modelOptions);
    }

}

    