package com.wc.demo.invoke;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;

import reactor.core.publisher.Flux;

/**
 * 自定义 Re2（Re-Reading）Advisor —— 通过重读策略提升大模型推理能力
 *
 * 核心思路：在用户消息中重复原始问题，让模型"再读一遍"，从而提升对问题的理解和推理质量。 参考 Re-Reading
 * 论文：https://arxiv.org/abs/2309.06275
 *
 * 效果：将用户消息从 "你好" 转换为： 你好 Read the question again: 你好
 *
 * Advisor 链的执行顺序由 getOrder() 决定，数值越小优先级越高（越先执行请求拦截，越后执行响应处理）
 */
public class Re2Advisor implements CallAdvisor, StreamAdvisor
{

    @Override
    public String getName()
    {
        return "Re2Advisor";
    }

    @Override
    public int getOrder()
    {
        return 0;
    }

    /**
     * 构建重读后的新请求 —— 将用户消息替换为重读格式
     *
     * 原始消息：你好 重读后： 你好 Read the question again: 你好
     *
     * @param chatClientRequest 原始请求
     * @return 修改后的新请求
     */
    private ChatClientRequest re2Request(ChatClientRequest chatClientRequest)
    {
        // 1.取出最后一个用户消息（即当前用户输入，前面的是历史记忆注入的）
        List<UserMessage> userMessages = chatClientRequest.prompt().getUserMessages();
        String userText = userMessages.get(userMessages.size() - 1).getText();
        System.out.println("Re2Advisor: 原始用户消息：" + userText);

        // 2.拼接重读提示词
        String re2Text = userText + "   Read the question again: " + userText;

        // 3.构建新的 UserMessage（只替换最后一个用户消息）
        UserMessage newUserMessage = UserMessage.builder().text(re2Text).build();

        // 4.用新的消息列表构建新 Prompt（保留系统消息等其他消息不变，只替换最后一个 UserMessage）
        List<UserMessage> updatedUserMessages = new ArrayList<>(userMessages);
        updatedUserMessages.set(updatedUserMessages.size() - 1, newUserMessage);

        List<Message> newMessages = new ArrayList<>(chatClientRequest.prompt().getInstructions());
        // 替换消息列表中的最后一个 UserMessage
        for (int i = newMessages.size() - 1; i >= 0; i--)
        {
            if (newMessages.get(i) instanceof UserMessage)
            {
                newMessages.set(i, newUserMessage);
                break;
            }
        }
        Prompt newPrompt = new Prompt(newMessages, chatClientRequest.prompt().getOptions());

        // 重写后的消息
        System.out.println("Re2Advisor: 重读后的用户消息：" + newUserMessage.getText());

        // 5.构建新的请求对象
        return chatClientRequest.mutate().prompt(newPrompt).build();
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest,
            StreamAdvisorChain streamAdvisorChain)
    {
        ChatClientRequest newRequest = re2Request(chatClientRequest);
        return streamAdvisorChain.nextStream(newRequest);
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain)
    {
        ChatClientRequest newRequest = re2Request(chatClientRequest);
        return callAdvisorChain.nextCall(newRequest);
    }

}
