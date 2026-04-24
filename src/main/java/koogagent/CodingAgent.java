package koogagent;

import ai.koog.agents.core.agent.AIAgent;
import ai.koog.agents.core.tools.ToolRegistry;
import ai.koog.agents.features.eventHandler.feature.EventHandler;
import ai.koog.agents.features.eventHandler.feature.EventHandlerConfig;
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient;
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels;
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor;
import ai.koog.prompt.llm.LLModel;
import ai.koog.prompt.message.Message;
import koogagent.tools.BashTool;
import koogagent.tools.CodeSearchTool;
import koogagent.tools.EditFileTool;
import koogagent.tools.ListFileTool;
import koogagent.tools.ReadFileTool;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class CodingAgent implements AutoCloseable {

    private static final LLModel MODEL = AnthropicModels.Opus_4_6;
    private static final String SYSTEM_PROMPT = "당신은 파일을 읽고 수정하는 코딩 에이전트입니다.";
    private static final int MAX_ITERATIONS = 50;

    private final AnthropicLLMClient client;
    private final MultiLLMPromptExecutor executor;
    private final ToolRegistry toolRegistry;

    public CodingAgent(AnthropicLLMClient client, String bashPath) {
        this.client = client;
        this.executor = new MultiLLMPromptExecutor(client);
        this.toolRegistry = ToolRegistry.builder()
            .tools(new ReadFileTool())
            .tools(new ListFileTool())
            .tools(new EditFileTool())
            .tools(new BashTool(bashPath))
            .tools(new CodeSearchTool())
            .build();
    }

    public String chat(String userMessage) throws Exception {
        AIAgent<String, String> agent = AIAgent.builder()
            .promptExecutor(executor)
            .llmModel(MODEL)
            .systemPrompt(SYSTEM_PROMPT)
            .temperature(0.3)
            .toolRegistry(toolRegistry)
            .maxIterations(MAX_ITERATIONS)
            // .install(EventHandler.Feature, (EventHandlerConfig config) -> {
            //     config.onLLMCallStarting(ctx ->
            //         log.info("→ LLM 호출: {}", ctx.getPrompt()));
            //     config.onLLMCallCompleted(ctx ->
            //         log.info("← LLM 응답: {}", ctx.getResponses()));
            //     config.onToolCallStarting(ctx ->
            //         log.info("도구 실행: {}({})", ctx.getToolName(), ctx.getToolArgs()));
            //     config.onToolCallCompleted(ctx ->
            //         log.info("도구 결과: {}", ctx.getToolResult()));
            // })
            .build();
        return agent.run(userMessage);
    }

    static String buildSystemPromptWithHistory(List<Message> history) {
        if (history.isEmpty()) return SYSTEM_PROMPT;
        StringBuilder sb = new StringBuilder();
        sb.append("# System Prompt\n").append(SYSTEM_PROMPT).append("\n\n");
        sb.append("# Conversation History\n");
        for (Message msg : history) {
            if (msg instanceof Message.User u) {
                sb.append("User: ").append(u.getContent()).append("\n");
            } else if (msg instanceof Message.Assistant a) {
                sb.append("Assistant: ").append(a.getContent()).append("\n");
            }
        }
        sb.append("\n위의 맥락을 바탕으로 대화를 이어가 주세요.\n");
        return sb.toString();
    }

    @Override
    public void close() throws Exception {
        executor.close();
        if (client instanceof AutoCloseable ac) {
            ac.close();
        }
    }
}
