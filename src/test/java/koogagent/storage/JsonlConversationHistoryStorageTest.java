package koogagent.storage;

import ai.koog.prompt.message.Message;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class JsonlConversationHistoryStorageTest {

    @TempDir
    Path tempDir;

    @Test
    void constructor_createsSessionDirectory() throws IOException {
        Path sessionDir = tempDir.resolve("session1");

        new JsonlConversationHistoryStorage(sessionDir);

        assertThat(Files.exists(sessionDir)).isTrue();
    }

    @Test
    void addConversation_createsFileWithTwoLines() throws IOException {
        Path sessionDir = tempDir.resolve("session2");
        var storage = new JsonlConversationHistoryStorage(sessionDir);

        storage.addConversation("안녕", "반갑습니다");

        Path historyFile = sessionDir.resolve("session.jsonl");
        assertThat(Files.exists(historyFile)).isTrue();
        var lines = Files.readAllLines(historyFile).stream()
            .filter(l -> !l.isBlank()).toList();
        assertThat(lines).hasSize(2);
        assertThat(lines.get(0)).contains("\"type\":\"user\"");
        assertThat(lines.get(1)).contains("\"type\":\"assistant\"");
    }

    @Test
    void addConversation_appendsOnSecondCall() throws IOException {
        Path sessionDir = tempDir.resolve("session3");
        var storage = new JsonlConversationHistoryStorage(sessionDir);

        storage.addConversation("첫 번째", "응답1");
        storage.addConversation("두 번째", "응답2");

        Path historyFile = sessionDir.resolve("session.jsonl");
        var lines = Files.readAllLines(historyFile).stream()
            .filter(l -> !l.isBlank()).toList();
        assertThat(lines).hasSize(4);
    }

    @Test
    void getHistory_returnsEmptyWhenNoFile() throws IOException {
        Path sessionDir = tempDir.resolve("session4");
        var storage = new JsonlConversationHistoryStorage(sessionDir);

        assertThat(storage.getHistory()).isEmpty();
    }

    @Test
    void getHistory_returnsMessagesInOrder() throws IOException {
        Path sessionDir = tempDir.resolve("session5");
        var storage = new JsonlConversationHistoryStorage(sessionDir);
        storage.addConversation("첫 질문", "첫 답변");

        var history = storage.getHistory();

        assertThat(history).hasSize(2);
        assertThat(history.get(0)).isInstanceOf(Message.User.class);
        assertThat(history.get(0).getContent()).isEqualTo("첫 질문");
        assertThat(history.get(1)).isInstanceOf(Message.Assistant.class);
        assertThat(history.get(1).getContent()).isEqualTo("첫 답변");
    }

    @Test
    void getHistory_skipsMalformedLines() throws IOException {
        Path sessionDir = tempDir.resolve("session6");
        var storage = new JsonlConversationHistoryStorage(sessionDir);
        storage.addConversation("정상", "메시지");
        Files.writeString(sessionDir.resolve("session.jsonl"),
            Files.readString(sessionDir.resolve("session.jsonl")) + "\n손상된줄");

        var history = storage.getHistory();

        assertThat(history).hasSize(2);
    }
}
