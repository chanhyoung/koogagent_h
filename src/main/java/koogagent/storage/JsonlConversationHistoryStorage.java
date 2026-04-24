package koogagent.storage;

import ai.koog.agents.core.agent.AIAgent;
import ai.koog.agents.core.tools.ToolRegistry;
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor;
import ai.koog.prompt.llm.LLModel;
import ai.koog.prompt.message.Message;
import ai.koog.prompt.message.RequestMetaInfo;
import ai.koog.prompt.message.ResponseMetaInfo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import koogagent.utils.MessageFormatter;
import kotlin.Unit;
import kotlinx.serialization.KSerializer;
import kotlinx.serialization.json.Json;
import kotlinx.serialization.json.JsonKt;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class JsonlConversationHistoryStorage implements ConversationHistoryStorage {

    private static final int COMPRESS_THRESHOLD = 10;
    private static final int KEEP_RECENT = 4;

    private final Path historyFile;
    private final Path summaryFile;
    private int cachedMessageCount = -1; // -1 = not yet loaded; avoids file read on each chat hot-path
    private final Json json;
    private final ObjectMapper mapper;

    @SuppressWarnings("unchecked")
    private static final KSerializer<Message.User> USER_SERIALIZER =
        (KSerializer<Message.User>) Message.User.Companion.serializer();

    @SuppressWarnings("unchecked")
    private static final KSerializer<Message.Assistant> ASSISTANT_SERIALIZER =
        (KSerializer<Message.Assistant>) Message.Assistant.Companion.serializer();

    public JsonlConversationHistoryStorage(Path sessionDir) throws IOException {
        Files.createDirectories(sessionDir);
        this.historyFile = sessionDir.resolve("session.jsonl");
        this.summaryFile = sessionDir.resolve("summary.md");
        this.json = JsonKt.Json(Json.Default, cfg -> {
            cfg.setIgnoreUnknownKeys(true);
            return Unit.INSTANCE;
        });
        this.mapper = new ObjectMapper();
    }

    @Override
    public void addConversation(String userMessage, String assistantMessage) throws IOException {
        Message.User user = new Message.User(userMessage, RequestMetaInfo.Companion.getEmpty());
        Message.Assistant assistant = new Message.Assistant(
            assistantMessage, ResponseMetaInfo.Companion.getEmpty(), null, null);

        String userLine = buildLine("user", json.encodeToString(USER_SERIALIZER, user));
        String assistantLine = buildLine("assistant", json.encodeToString(ASSISTANT_SERIALIZER, assistant));

        String existing = Files.exists(historyFile) ? Files.readString(historyFile) : "";
        String newContent = existing.isEmpty()
            ? userLine + "\n" + assistantLine
            : existing + "\n" + userLine + "\n" + assistantLine;

        Files.writeString(historyFile, newContent);
        if (cachedMessageCount >= 0) cachedMessageCount += 2;
    }

    @Override
    public List<Message> getHistory() throws IOException {
        List<Message> all = loadAllMessages();
        if (all.size() <= KEEP_RECENT) return all;
        return all.subList(all.size() - KEEP_RECENT, all.size());
    }

    @Override
    public String getSummary() throws IOException {
        try {
            String content = Files.readString(summaryFile).strip();
            return content.isBlank() ? null : content;
        } catch (NoSuchFileException e) {
            return null;
        }
    }

    @Override
    public void compressHistory(MultiLLMPromptExecutor executor, LLModel model) throws Exception {
        if (cachedMessageCount() <= COMPRESS_THRESHOLD) return;

        List<Message> all = loadAllMessages();
        if (all.size() <= COMPRESS_THRESHOLD) return;

        List<Message> toSummarize = all.subList(0, all.size() - KEEP_RECENT);

        StringBuilder conversationText = new StringBuilder();
        String existing = getSummary();
        if (existing != null) {
            conversationText.append("이전 요약: ").append(existing).append("\n\n");
        }
        MessageFormatter.appendMessages(conversationText, toSummarize);

        String summary = callSummarizeLLM(conversationText.toString(), executor, model);
        Files.writeString(summaryFile, summary);
        cachedMessageCount = KEEP_RECENT;
    }

    private int cachedMessageCount() throws IOException {
        if (cachedMessageCount < 0) {
            if (!Files.exists(historyFile)) {
                cachedMessageCount = 0;
            } else {
                try (var lines = Files.lines(historyFile)) {
                    cachedMessageCount = (int) lines.filter(l -> !l.isBlank()).count();
                }
            }
        }
        return cachedMessageCount;
    }

    protected String callSummarizeLLM(String conversationText, MultiLLMPromptExecutor executor, LLModel model) throws Exception {
        AIAgent<String, String> agent = AIAgent.builder()
            .promptExecutor(executor)
            .llmModel(model)
            .systemPrompt(conversationText)
            .toolRegistry(ToolRegistry.builder().build())
            .maxIterations(1)
            .build();
        return agent.run("이 대화를 간결하게 요약해주세요. 핵심 정보만 간결하게 작성하세요.");
    }

    private List<Message> loadAllMessages() throws IOException {
        if (!Files.exists(historyFile)) return List.of();

        List<Message> messages = new ArrayList<>();
        for (String line : Files.readAllLines(historyFile)) {
            if (line.isBlank()) continue;
            try {
                JsonNode node = mapper.readTree(line);
                String type = node.get("type").asText();
                String dataJson = mapper.writeValueAsString(node.get("data"));
                Message msg = switch (type) {
                    case "user" -> json.decodeFromString(USER_SERIALIZER, dataJson);
                    case "assistant" -> json.decodeFromString(ASSISTANT_SERIALIZER, dataJson);
                    default -> throw new IllegalArgumentException("알 수 없는 타입: " + type);
                };
                messages.add(msg);
            } catch (Exception e) {
                System.err.println("해당 줄을 구문 분석하는 데 실패했습니다: " + line);
            }
        }
        return messages;
    }

    private String buildLine(String type, String dataJson) throws IOException {
        ObjectNode node = mapper.createObjectNode();
        node.put("timestamp", Instant.now().toString());
        node.put("type", type);
        node.set("data", mapper.readTree(dataJson));
        return mapper.writeValueAsString(node);
    }
}
