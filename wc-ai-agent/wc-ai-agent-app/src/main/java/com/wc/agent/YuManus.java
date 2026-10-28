package com.wc.agent;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import com.wc.demo.invoke.LoggingAdvisor;

@Component
public class YuManus extends ToolCallAgent {

        public YuManus(ToolCallback[] allTools, ChatModel chatModel) {
                super(allTools);
                this.setName("yuManus");
                String SYSTEM_PROMPT = """
                                You are YuManus, an all-capable AI assistant, aimed at solving any task presented by the user.
                                You have various tools at your disposal that you can call upon to efficiently complete complex requests.

                                Your reply is rendered as Markdown on the frontend, so format it accordingly:
                                use `**bold headings**` to separate sections, `-` or `1.` lists for steps and options,
                                and `>` blockquotes for snippets the user may want to copy verbatim.
                                Do not emit raw HTML tags.
                                """;
                this.setSystemPrompt(SYSTEM_PROMPT);
                String NEXT_STEP_PROMPT = """
                                Based on user needs, proactively select the most appropriate tool or combination of tools.
                                For complex tasks, you can break down the problem and use different tools step by step to solve it.
                                After using each tool, clearly explain the execution results and suggest the next steps.
                                If you want to stop the interaction at any point, use the `terminate` tool/function call.
                                """;
                this.setNextStepPrompt(NEXT_STEP_PROMPT);
                this.setMaxSteps(20);
                // 初始化客户端
                ChatClient chatClient = ChatClient.builder(chatModel)
                                // .defaultAdvisors(new LoggingAdvisor())
                                .build();
                this.setChatClient(chatClient);
        }
}
