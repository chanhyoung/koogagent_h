package koogagent;

import ai.koog.agents.core.agent.AIAgent;
import ai.koog.agents.core.tools.ToolRegistry;
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient;
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels;
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor;
import ai.koog.prompt.llm.LLModel;
import ai.koog.prompt.message.Message;
import koogagent.storage.ConversationHistoryStorage;
import koogagent.storage.JsonlConversationHistoryStorage;
import koogagent.tools.BashTool;
import koogagent.tools.CodeSearchTool;
import koogagent.tools.EditFileTool;
import koogagent.tools.ListFileTool;
import koogagent.tools.ReadFileTool;
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

    private final AnthropicLLMClient client;
    private final MultiLLMPromptExecutor executor;
    private final ToolRegistry toolRegistry;
    private final ConversationHistoryStorage conversationHistoryStorage;

    public CodingAgent(AnthropicLLMClient client, String bashPath) throws IOException {
        this(client, bashPath, "default", UUID.randomUUID().toString());
    }

    public CodingAgent(AnthropicLLMClient client, String bashPath, String projectDir, String sessionId) throws IOException {
        this.client = client;
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
    }

    public String chat(String userMessage) throws Exception {
        List<Message> history = conversationHistoryStorage.getHistory();
        String system = buildSystemPromptWithHistory(history);

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
    }
}
