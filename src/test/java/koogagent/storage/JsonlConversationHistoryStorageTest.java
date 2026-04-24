package koogagent.storage;

import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor;
import ai.koog.prompt.llm.LLModel;
import ai.koog.prompt.message.Message;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JsonlConversationHistoryStorageTest {

    @TempDir
    Path tempDir;

    // ── addConversation / 기존 ────────────────────────────────────────────

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
        var lines = Files.readAllLines(historyFile, StandardCharsets.UTF_8).stream()
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
        var lines = Files.readAllLines(historyFile, StandardCharsets.UTF_8).stream()
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
    void getHistory_skipsMalformedLines() throws IOException {
        Path sessionDir = tempDir.resolve("session6");
        var storage = new JsonlConversationHistoryStorage(sessionDir);
        storage.addConversation("정상", "메시지");
        Files.writeString(sessionDir.resolve("session.jsonl"),
            Files.readString(sessionDir.resolve("session.jsonl"), StandardCharsets.UTF_8) + "\n손상된줄",
            StandardCharsets.UTF_8);

        var history = storage.getHistory();

        assertThat(history).hasSize(2);
    }

    // ── getHistory: Sliding Window ────────────────────────────────────────

    @Test
    void getHistory_returnsAllWhenFourOrLess() throws IOException {
        Path sessionDir = tempDir.resolve("session-small");
        var storage = new JsonlConversationHistoryStorage(sessionDir);
        storage.addConversation("q1", "a1");
        storage.addConversation("q2", "a2"); // 4 messages

        var history = storage.getHistory();

        assertThat(history).hasSize(4);
        assertThat(history.get(0).getContent()).isEqualTo("q1");
    }

    @Test
    void getHistory_returnsLastFourWhenMoreThanFour() throws IOException {
        Path sessionDir = tempDir.resolve("session-large");
        var storage = new JsonlConversationHistoryStorage(sessionDir);
        storage.addConversation("q1", "a1");
        storage.addConversation("q2", "a2");
        storage.addConversation("q3", "a3"); // 6 messages

        var history = storage.getHistory();

        assertThat(history).hasSize(4);
        assertThat(history.get(0).getContent()).isEqualTo("q2");
        assertThat(history.get(1).getContent()).isEqualTo("a2");
        assertThat(history.get(2).getContent()).isEqualTo("q3");
        assertThat(history.get(3).getContent()).isEqualTo("a3");
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

    // ── getSummary ────────────────────────────────────────────────────────

    @Test
    void getSummary_returnsNullWhenNoFile() throws IOException {
        var storage = new JsonlConversationHistoryStorage(tempDir.resolve("no-summary"));

        assertThat(storage.getSummary()).isNull();
    }

    @Test
    void getSummary_returnsNullWhenBlank() throws IOException {
        Path sessionDir = tempDir.resolve("blank-summary");
        var storage = new JsonlConversationHistoryStorage(sessionDir);
        Files.writeString(sessionDir.resolve("summary.md"), "   ", StandardCharsets.UTF_8);

        assertThat(storage.getSummary()).isNull();
    }

    @Test
    void getSummary_returnsContentWhenExists() throws IOException {
        Path sessionDir = tempDir.resolve("has-summary");
        var storage = new JsonlConversationHistoryStorage(sessionDir);
        Files.writeString(sessionDir.resolve("summary.md"), "이전 대화 요약입니다.", StandardCharsets.UTF_8);

        assertThat(storage.getSummary()).isEqualTo("이전 대화 요약입니다.");
    }

    // ── compressHistory ───────────────────────────────────────────────────

    @Test
    void compressHistory_doesNothingWhenTenOrLess() throws Exception {
        Path sessionDir = tempDir.resolve("compress-skip");
        var storage = new JsonlConversationHistoryStorage(sessionDir);
        for (int i = 0; i < 5; i++) { // 5 conversations = 10 messages (exactly at threshold)
            storage.addConversation("q" + i, "a" + i);
        }

        storage.compressHistory(null, null); // LLM 호출 없어야 함

        assertThat(Files.exists(sessionDir.resolve("summary.md"))).isFalse();
    }

    @Test
    void compressHistory_createsSummaryWhenOverThreshold() throws Exception {
        Path sessionDir = tempDir.resolve("compress-create");
        var storage = stubbedStorage(sessionDir, "테스트 요약 결과");
        for (int i = 0; i < 6; i++) { // 12 messages > threshold(10)
            storage.addConversation("q" + i, "a" + i);
        }

        storage.compressHistory(null, null);

        assertThat(Files.exists(sessionDir.resolve("summary.md"))).isTrue();
        assertThat(Files.readString(sessionDir.resolve("summary.md"), StandardCharsets.UTF_8)).isEqualTo("테스트 요약 결과");
    }

    @Test
    void compressHistory_includesPreviousSummary() throws Exception {
        Path sessionDir = tempDir.resolve("compress-accumulate");
        List<String> captured = new ArrayList<>();
        var storage = new JsonlConversationHistoryStorage(sessionDir) {
            @Override
            protected String callSummarizeLLM(String text, MultiLLMPromptExecutor ex, LLModel m) {
                captured.add(text);
                return "새 요약";
            }
        };
        Files.writeString(sessionDir.resolve("summary.md"), "이전 요약", StandardCharsets.UTF_8);
        for (int i = 0; i < 6; i++) {
            storage.addConversation("q" + i, "a" + i);
        }

        storage.compressHistory(null, null);

        assertThat(captured.get(0)).contains("이전 요약");
    }

    // ── helper ────────────────────────────────────────────────────────────

    private JsonlConversationHistoryStorage stubbedStorage(Path dir, String fixedSummary) throws IOException {
        return new JsonlConversationHistoryStorage(dir) {
            @Override
            protected String callSummarizeLLM(String text, MultiLLMPromptExecutor ex, LLModel m) {
                return fixedSummary;
            }
        };
    }
}
