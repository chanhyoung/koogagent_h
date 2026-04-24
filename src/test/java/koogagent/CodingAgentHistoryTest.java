package koogagent;

import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient;
import ai.koog.prompt.message.Message;
import ai.koog.prompt.message.RequestMetaInfo;
import ai.koog.prompt.message.ResponseMetaInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CodingAgentHistoryTest {

    @TempDir
    Path tempDir;

    @Test
    void buildSystemPromptWithHistory_returnsBasePromptWhenEmpty() {
        String result = CodingAgent.buildSystemPromptWithHistory(List.of());

        assertThat(result).isEqualTo("당신은 파일을 읽고 수정하는 코딩 에이전트입니다.");
    }

    @Test
    void buildSystemPromptWithHistory_includesHistoryWhenPresent() {
        Message.User user = new Message.User("파일 읽어줘", RequestMetaInfo.Companion.getEmpty());
        Message.Assistant asst = new Message.Assistant(
            "파일 내용입니다", ResponseMetaInfo.Companion.getEmpty(), null, null);

        String result = CodingAgent.buildSystemPromptWithHistory(List.of(user, asst));

        assertThat(result).contains("# System Prompt");
        assertThat(result).contains("# Conversation History");
        assertThat(result).contains("User: 파일 읽어줘");
        assertThat(result).contains("Assistant: 파일 내용입니다");
        assertThat(result).contains("위의 맥락을 바탕으로 대화를 이어가 주세요.");
    }

    @Test
    void constructor_createsStorageDirectory() throws Exception {
        AnthropicLLMClient mockClient = Mockito.mock(AnthropicLLMClient.class);
        String projectDir = "test-project";
        String sessionId = "test-session";

        try (var agent = new CodingAgent(mockClient, "bash", projectDir, sessionId)) {
            Path expectedDir = Path.of(".koogagent/projects/" + projectDir + "/" + sessionId);
            assertThat(Files.exists(expectedDir)).isTrue();
            deleteRecursively(Path.of(".koogagent"));
        }
    }

    private void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) return;
        try (var walk = Files.walk(path)) {
            walk.sorted(java.util.Comparator.reverseOrder())
                .forEach(p -> { try { Files.delete(p); } catch (IOException ignored) {} });
        }
    }
}
