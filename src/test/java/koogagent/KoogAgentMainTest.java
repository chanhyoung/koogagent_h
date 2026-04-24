package koogagent;

import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient;
import koogagent.storage.JsonlConversationHistoryStorage;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class KoogAgentMainTest {

    @Test
    void clearCommand_printsConfirmationAndCreatesNewAgent() throws Exception {
        AnthropicLLMClient mockClient = Mockito.mock(AnthropicLLMClient.class);
        AtomicInteger agentCreationCount = new AtomicInteger(0);

        InputStream in = new ByteArrayInputStream("/clear\nexit\n".getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outBuffer, true, StandardCharsets.UTF_8);

        KoogAgentMain.runLoop(in, out, () -> {
            agentCreationCount.incrementAndGet();
            return new CodingAgent(mockClient, "bash", "clear-test", UUID.randomUUID().toString());
        });

        assertThat(agentCreationCount.get()).isEqualTo(2);
        assertThat(outBuffer.toString(StandardCharsets.UTF_8)).contains("새로운 대화가 시작되었습니다.");

        deleteRecursively(Path.of(".koogagent/projects/clear-test"));
    }

    @Test
    void clearCommand_preservesPreviousSessionFile() throws Exception {
        AnthropicLLMClient mockClient = Mockito.mock(AnthropicLLMClient.class);
        String oldSessionId = "old-" + UUID.randomUUID();
        String newSessionId = "new-" + UUID.randomUUID();
        String[] sessionIds = {oldSessionId, newSessionId};
        AtomicInteger idx = new AtomicInteger(0);

        Path oldSessionDir = Path.of(".koogagent/projects/preserve-test/" + oldSessionId);
        new JsonlConversationHistoryStorage(oldSessionDir).addConversation("이전 질문", "이전 답변");

        InputStream in = new ByteArrayInputStream("/clear\nexit\n".getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outBuffer, true, StandardCharsets.UTF_8);

        KoogAgentMain.runLoop(in, out, () ->
            new CodingAgent(mockClient, "bash", "preserve-test", sessionIds[idx.getAndIncrement()])
        );

        assertThat(Files.exists(oldSessionDir.resolve("session.jsonl"))).isTrue();

        deleteRecursively(Path.of(".koogagent/projects/preserve-test"));
    }

    @Test
    void startupMessage_includesClearInstruction() throws Exception {
        AnthropicLLMClient mockClient = Mockito.mock(AnthropicLLMClient.class);

        InputStream in = new ByteArrayInputStream("exit\n".getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outBuffer, true, StandardCharsets.UTF_8);

        KoogAgentMain.runLoop(in, out, () ->
            new CodingAgent(mockClient, "bash", "startup-test", UUID.randomUUID().toString())
        );

        assertThat(outBuffer.toString(StandardCharsets.UTF_8)).contains("/clear");

        deleteRecursively(Path.of(".koogagent/projects/startup-test"));
    }

    private void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) return;
        try (var walk = Files.walk(path)) {
            walk.sorted(java.util.Comparator.reverseOrder())
                .forEach(p -> { try { Files.delete(p); } catch (IOException ignored) {} });
        }
    }
}
