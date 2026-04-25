package koogagent;

import ai.koog.agents.core.agent.AIAgent;
import ai.koog.agents.core.tools.ToolRegistry;
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient;
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels;
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor;
import ai.koog.prompt.llm.LLModel;
import ai.koog.prompt.message.Message;
import koogagent.storage.AgentMemoryStorage;
import koogagent.storage.ConversationHistoryStorage;
import koogagent.storage.JsonlConversationHistoryStorage;
import koogagent.storage.KodingMemoryStorage;
import koogagent.tools.BashTool;
import koogagent.tools.CodeSearchTool;
import koogagent.tools.EditFileTool;
import koogagent.tools.ListFileTool;
import koogagent.tools.ReadFileTool;
import koogagent.utils.MessageFormatter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

@Slf4j
public class CodingAgent implements AutoCloseable {

    private static final LLModel MODEL = AnthropicModels.Opus_4_6;
    static final String SYSTEM_PROMPT = "당신은 파일을 읽고 수정하는 코딩 에이전트입니다.";
    private static final int MAX_ITERATIONS = 50;

    private final MultiLLMPromptExecutor executor;
    private final ToolRegistry toolRegistry;
    private final ConversationHistoryStorage conversationHistoryStorage;
    final AgentMemoryStorage agentMemoryStorage;

    public CodingAgent(AnthropicLLMClient client, String bashPath) throws IOException {
        this(client, bashPath, "default", UUID.randomUUID().toString());
    }

    public CodingAgent(AnthropicLLMClient client, String bashPath, String projectDir, String sessionId) throws IOException {
        this.executor = new MultiLLMPromptExecutor(client);
        this.toolRegistry = ToolRegistry.builder()
            .tools(new ReadFileTool())
            .tools(new ListFileTool())
            .tools(new EditFileTool())
            .tools(new BashTool(bashPath))
            .tools(new CodeSearchTool())
            .build();
        this.conversationHistoryStorage = new JsonlConversationHistoryStorage(
            Path.of(".koogagent/projects/" + projectDir + "/" + sessionId)
        );
        this.agentMemoryStorage = new KodingMemoryStorage();
    }

    public String chat(String userMessage) throws Exception {
        conversationHistoryStorage.compressHistory(executor, MODEL);

        List<Message> history = conversationHistoryStorage.getHistory();
        String summary = conversationHistoryStorage.getSummary();
        String memory = agentMemoryStorage.getMemory();
        String system = buildSystemPromptWithHistory(history, summary, memory);

        AIAgent<String, String> agent = AIAgent.builder()
            .promptExecutor(executor)
            .llmModel(MODEL)
            .systemPrompt(system)
            .temperature(0.3)
            .toolRegistry(toolRegistry)
            .maxIterations(MAX_ITERATIONS)
            .build();

        String response = agent.run(userMessage);
        conversationHistoryStorage.addConversation(userMessage, response);
        return response;
    }

    static String buildSystemPromptWithHistory(List<Message> history, String summary, String memory) {
        if (summary == null && history.isEmpty() && memory == null) return SYSTEM_PROMPT;

        StringBuilder sb = new StringBuilder();
        sb.append("# System Prompt\n").append(SYSTEM_PROMPT);

        if (memory != null) {
            sb.append("\n\n# Project Memory\n");
            sb.append("아래는 이 프로젝트에 대해 기억해야 할 정보입니다:\n");
            sb.append(memory);
        }

        if (summary != null) {
            sb.append("\n\n# Previous Conversation Summary\n").append(summary);
        }

        if (!history.isEmpty()) {
            sb.append("\n\n# Recent Conversation\n");
            MessageFormatter.appendMessages(sb, history);
        }

        return sb.toString();
    }

    @Override
    public void close() throws Exception {
        executor.close();
    }
}
