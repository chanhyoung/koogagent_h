package koogagent.storage;

import ai.koog.prompt.message.Message;
import ai.koog.prompt.message.RequestMetaInfo;
import ai.koog.prompt.message.ResponseMetaInfo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import kotlin.Unit;
import kotlinx.serialization.KSerializer;
import kotlinx.serialization.json.Json;
import kotlinx.serialization.json.JsonKt;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class JsonlConversationHistoryStorage implements ConversationHistoryStorage {

    private final Path historyFile;
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
    }

    @Override
    public List<Message> getHistory() throws IOException {
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
