package koogagent.storage;

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
}
