package com.wc.controller.AiController;

import java.io.IOException;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.wc.agent.YuManus;
import com.wc.app.LoveApp;

import jakarta.annotation.Resource;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/ai")
public class AiController {

    @Resource
    private LoveApp loveApp;

    @Resource
    private ToolCallback[] allTools;

    @Resource
    private ChatModel dashscopeChatModel;

    /**
     * 同步调用接口
     * 
     * @param message
     * @param chatId
     * @return
     */
    @GetMapping("love_app/chat/sync")
    public String doChatWithLoveAppSync(String message, String chatId) {
        return loveApp.doChat(message, chatId);
    }

    /**
     * 异步调用接口（SSE 流式输出）
     *
     * 通过 produces = MediaType.TEXT_EVENT_STREAM_VALUE 将响应头 Content-Type
     * 设置为 "text/event-stream"（SSE 协议的标准 MIME 类型），含义如下：
     * 1. 协议约定：前端（如 EventSource）只接受 Content-Type 为 text/event-stream
     * 的响应，才会按 SSE 事件流解析；默认的 text/plain 或 JSON 无法被识别。
     * 2. 内容协商：produces 参与 Spring 的内容协商机制，若客户端请求的 Accept 头
     * 与此声明的类型不匹配，Spring 会直接返回 406 Not Acceptable。
     * 3. 流式响应：返回 Flux<String> 时，Spring WebMVC 不会等所有数据生成完再返回，
     * 而是数据产生一条就推送一条，实现 AI 大模型逐 token 生成、前端"打字机"式输出。
     *
     * @param message 用户输入的消息
     * @param chatId  会话 ID，用于区分不同会话（支持多轮对话记忆）
     * @return Flux<String> SSE 事件流，AI 生成的内容片段会逐条推送
     */
    @GetMapping(value = "/love_app/chat/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> doChatWithLoveAppSSE(String message, String chatId) {
        return loveApp.doChatByStream(message, chatId);
    }

    /**
     * 服务器主动向客户端发送消息（SseEmitter 手动推送方式）
     *
     * SseEmitter 是 Spring MVC 提供的"服务端推送事件句柄"，与直接返回 Flux 的区别
     * 在于谁来控制数据流的推送和生命周期：
     * - 直接返回 Flux：Spring 自动订阅并推送，代码简洁但推送逻辑不受控制；
     * - 返回 SseEmitter：需自己调用 emitter.send() 推送，可手动控制发送时机，
     * 适合结合非响应式代码、跨线程推送、数据加工/过滤等场景。
     *
     * 返回 SseEmitter 后 Spring 的处理流程：
     * 1. 立即让本次 HTTP 请求进入异步模式，主线程被释放，响应连接保持打开；
     * 2. 客户端收到 Content-Type: text/event-stream 的 SSE 响应（返回类型为
     * SseEmitter 时 Spring 会自动设置该响应头，无需再加 produces 注解）；
     * 3. 之后任何持有该 emitter 的线程调用 send() 都可向客户端推送数据；
     * 4. 直到调用 complete()（正常结束）或 completeWithError()（异常结束）。
     *
     * 注意：方法返回 emitter 时订阅已经（在下方 subscribe() 处）开始，推送动作
     * 与返回动作是并行的——先交出连接，再异步地往连接里写数据。
     *
     * @param message 用户输入的消息
     * @param chatId  会话 ID，用于区分不同会话（支持多轮对话记忆）
     * @return SseEmitter 推送器，AI 生成的内容片段会通过它逐条推送给客户端
     */
    @GetMapping("/love_app/chat/sse/emitter")
    public SseEmitter doChatWithLoveAppSseEmitter(String message, String chatId) {
        // 创建一个超时时间较长的 SseEmitter，180000L（毫秒）= 3 分钟。
        // 含义：这条 SSE 连接最多保持 3 分钟，若期间 complete() 未被调用，
        // Spring 会自动结束连接并触发超时回调。默认无参构造是不超时，
        // 容易导致连接泄漏，因此需要显式设置。
        SseEmitter emitter = new SseEmitter(180000L); // 3分钟超时
        // 获取 Flux 数据流并手动订阅。subscribe() 的三个参数正好对应
        // 响应式流的三个回调：onNext、onError、onComplete。
        loveApp.doChatByStream(message, chatId)
                .subscribe(
                        // onNext：AI 每生成出一小段内容（chunk），就通过 emitter
                        // 推送给客户端。send() 会抛 IOException（如客户端已断开），
                        // 此时调用 completeWithError(e) 提前终止本次推送。
                        chunk -> {
                            try {
                                emitter.send(chunk);
                            } catch (IOException e) {
                                emitter.completeWithError(e);
                            }
                        },
                        // onError：若 Flux 流本身出错（如大模型 API 调用失败），
                        // 通知 emitter 以错误方式结束 SSE 连接。
                        emitter::completeWithError,
                        // onComplete：AI 回答全部生成完毕、Flux 流结束时，
                        // 调用 complete() 关闭 SSE 连接，客户端会收到关闭信号。
                        emitter::complete);
        // 把 emitter 返回给 Spring，由它接管这条 SSE 连接的异步响应
        return emitter;
    }

    /**
     * 流式调用 Manus 超级智能体
     *
     * @param message
     * @return
     */
    @GetMapping("/manus/chat")
    public SseEmitter doChatWithManus(String message) {
        YuManus yuManus = new YuManus(allTools, dashscopeChatModel);
        return yuManus.runStream(message);
    }

}
