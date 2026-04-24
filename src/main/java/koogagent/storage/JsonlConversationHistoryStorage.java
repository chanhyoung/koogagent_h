package koogagent.storage;

import ai.koog.prompt.message.Message;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
        throw new UnsupportedOperationException("미구현");
    }

    @Override
    public List<Message> getHistory() throws IOException {
        throw new UnsupportedOperationException("미구현");
    }
}
