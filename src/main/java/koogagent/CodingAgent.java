package koogagent;

import ai.koog.agents.core.agent.AIAgent;
import ai.koog.agents.core.tools.ToolRegistry;
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient;
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels;
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor;
import ai.koog.prompt.llm.LLModel;
import koogagent.tools.BashTool;
import koogagent.tools.CodeSearchTool;
import koogagent.tools.EditFileTool;
import koogagent.tools.ListFileTool;
import koogagent.tools.ReadFileTool;

public class CodingAgent implements AutoCloseable {

    private static final LLModel MODEL = AnthropicModels.Opus_4_6;
    private static final String SYSTEM_PROMPT = "당신은 파일을 읽고 수정하는 코딩 에이전트입니다.";
    private static final int MAX_ITERATIONS = 50;

    private final MultiLLMPromptExecutor executor;
    private final ToolRegistry toolRegistry;

    public CodingAgent(AnthropicLLMClient client) {
        this.executor = new MultiLLMPromptExecutor(client);
        this.toolRegistry = ToolRegistry.builder()
            .tools(new ReadFileTool())
            .tools(new ListFileTool())
            .tools(new EditFileTool())
            .tools(new BashTool())
            .tools(new CodeSearchTool())
            .build();
    }

    public String chat(String userMessage) throws Exception {
        AIAgent<String, String> agent = AIAgent.<String, String>builder()
            .promptExecutor(executor)
            .llmModel(MODEL)
            .systemPrompt(SYSTEM_PROMPT)
            .temperature(0.3)
            .toolRegistry(toolRegistry)
            .maxIterations(MAX_ITERATIONS)
            .build();
        return agent.run(userMessage);
    }

    @Override
    public void close() throws Exception {
        executor.close();
    }
}
