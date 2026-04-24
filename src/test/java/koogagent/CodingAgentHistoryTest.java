package koogagent;

import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient;
import ai.koog.prompt.message.Message;
import ai.koog.prompt.message.RequestMetaInfo;
import ai.koog.prompt.message.ResponseMetaInfo;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CodingAgentHistoryTest {

    private static Message.User user(String content) {
        return new Message.User(content, RequestMetaInfo.Companion.getEmpty());
    }

    private static Message.Assistant assistant(String content) {
        return new Message.Assistant(content, ResponseMetaInfo.Companion.getEmpty(), null, null);
    }

    @Test
    void buildSystemPromptWithHistory_noHistoryNoSummary() {
        String result = CodingAgent.buildSystemPromptWithHistory(List.of(), null);

        assertThat(result).isEqualTo(CodingAgent.SYSTEM_PROMPT);
    }

    @Test
    void buildSystemPromptWithHistory_withSummaryOnly() {
        String result = CodingAgent.buildSystemPromptWithHistory(List.of(), "이전 대화 요약");

        assertThat(result).contains("# Previous Conversation Summary");
        assertThat(result).contains("이전 대화 요약");
        assertThat(result).doesNotContain("# Recent Conversation");
    }

    @Test
    void buildSystemPromptWithHistory_withHistoryOnly() {
        String result = CodingAgent.buildSystemPromptWithHistory(
            List.of(user("질문"), assistant("답변")), null);

        assertThat(result).contains("# Recent Conversation");
        assertThat(result).contains("User: 질문");
        assertThat(result).contains("Assistant: 답변");
        assertThat(result).doesNotContain("# Previous Conversation Summary");
    }

    @Test
    void buildSystemPromptWithHistory_withBoth_summaryBeforeHistory() {
        String result = CodingAgent.buildSystemPromptWithHistory(
            List.of(user("최근 질문"), assistant("최근 답변")), "요약 내용");

        assertThat(result).contains("# Previous Conversation Summary");
        assertThat(result).contains("요약 내용");
        assertThat(result).contains("# Recent Conversation");
        assertThat(result).contains("User: 최근 질문");
        assertThat(result.indexOf("# Previous Conversation Summary"))
            .isLessThan(result.indexOf("# Recent Conversation"));
    }

    @Test
    void constructor_createsStorageDirectory() throws Exception {
        AnthropicLLMClient mockClient = Mockito.mock(AnthropicLLMClient.class);

        try (var agent = new CodingAgent(mockClient, "bash", "test-project", "test-session")) {
            Path expectedDir = Path.of(".koogagent/projects/test-project/test-session");
            assertThat(Files.exists(expectedDir)).isTrue();
            deleteRecursively(Path.of(".koogagent"));
        }
    }

    @Test
    void close_closesClientExactlyOnce() throws Exception {
        AnthropicLLMClient mockClient = Mockito.mock(AnthropicLLMClient.class);

        CodingAgent agent = new CodingAgent(mockClient, "bash", "test-close", "session-close");
        agent.close();

        Mockito.verify(mockClient, Mockito.times(1)).close();

        deleteRecursively(Path.of(".koogagent/projects/test-close"));
    }

    private void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) return;
        try (var walk = Files.walk(path)) {
            walk.sorted(java.util.Comparator.reverseOrder())
                .forEach(p -> { try { Files.delete(p); } catch (IOException ignored) {} });
        }
    }
}
