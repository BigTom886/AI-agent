package com.wc.demo.invoke;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;

import reactor.core.publisher.Flux;

/**
 * 自定义日志 Advisor —— 在 AI 模型调用前后打印请求和响应信息
 *
 * Advisor 是 Spring AI 的拦截器机制，类似于 Servlet Filter 或 Spring AOP 的 Around Advice： -
 * 在请求发送给模型之前，可以查看/修改请求（如添加日志、修改提示词） - 在模型返回响应之后，可以查看/修改响应（如记录耗时、过滤敏感词）
 *
 * Spring AI 1.1.8 提供两个 Advisor 接口： - CallAdvisor ——
 * 拦截同步调用（chatClient.prompt().call()） - StreamAdvisor ——
 * 拦截流式调用（chatClient.prompt().stream()）
 *
 * Advisor 链的执行顺序由 getOrder() 决定，数值越小优先级越高（越先执行请求拦截，越后执行响应处理）
 *
 * 注册方式：ChatClient.builder(chatModel).defaultAdvisors(new
 * LoggingAdvisor()).build()
 */
public class LoggingAdvisor implements CallAdvisor, StreamAdvisor
{

    /**
     * Advisor 的名称标识 用于在日志和调试中识别当前 Advisor，建议返回有意义的名称
     *
     * @return Advisor 名称
     */
    @Override
    public String getName()
    {
        return "LoggingAdvisor";
    }

    /**
     * Advisor 的执行优先级 数值越小，优先级越高（越早拦截请求，越晚处理响应） 例如：0 最高优先级，Integer.MAX_VALUE 最低优先级
     *
     * @return 优先级数值
     */
    @Override
    public int getOrder()
    {
        return 1;
    }

    /**
     * 拦截流式调用（chatClient.prompt().stream()）
     *
     * 执行流程： 1. 请求阶段：打印用户输入等请求信息 2. 调用下一个
     * Advisor：streamAdvisorChain.nextStream(request) 3. 响应阶段：打印模型返回的流式响应信息
     *
     * @param chatClientRequest 当前的请求对象，包含用户消息、系统提示词、Advisor 参数等
     * @param streamAdvisorChain Advisor 链，调用 nextStream() 将请求传递给下一个 Advisor（最终到达模型）
     * @return Flux<ChatClientResponse> 流式响应
     */
    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest,
            StreamAdvisorChain streamAdvisorChain)
    {
        // 请求阶段：记录所有消息（包括系统消息、历史用户消息、历史助手回复、当前用户消息）
        System.out.println("[LoggingAdvisor-Stream] 请求: " + chatClientRequest.prompt().getInstructions());
        // 调用链的下一个节点（可能是另一个 Advisor，也可能是模型本身）
        Flux<ChatClientResponse> response = streamAdvisorChain.nextStream(chatClientRequest);
        // 响应阶段：记录模型输出
        return response.doOnNext(r -> System.out.println("[LoggingAdvisor-Stream] 响应: " + r));
    }

    /**
     * 拦截同步调用（chatClient.prompt().call()）
     *
     * 执行流程： 1. 请求阶段：打印用户输入等请求信息 2. 调用下一个 Advisor：callAdvisorChain.nextCall(request)
     * 3. 响应阶段：打印模型返回的响应信息
     *
     * @param chatClientRequest 当前的请求对象，包含用户消息、系统提示词、Advisor 参数等
     * @param callAdvisorChain Advisor 链，调用 nextCall() 将请求传递给下一个 Advisor（最终到达模型）
     * @return ChatClientResponse 同步响应
     */
    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain)
    {
        // 请求阶段：记录所有消息（包括系统消息、历史用户消息、历史助手回复、当前用户消息）
        System.out.println("[LoggingAdvisor-Call] 请求: " + chatClientRequest.prompt().getInstructions());
        // 调用链的下一个节点（可能是另一个 Advisor，也可能是模型本身）
        ChatClientResponse response = callAdvisorChain.nextCall(chatClientRequest);
        // 响应阶段：记录模型输出
        System.out.println("[LoggingAdvisor-Call] 响应: " + response.chatResponse().getResult().getOutput().getText());
        return response;
    }

}
