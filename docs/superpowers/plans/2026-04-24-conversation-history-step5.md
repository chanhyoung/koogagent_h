# Conversation History Step 5 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `CodingAgent`에 `JsonlConversationHistoryStorage`를 통합하여 세션 간 대화 이력을 유지한다.

**Architecture:** 생성자 오버로드로 `projectDir`/`sessionId`를 추가하고, `chat` 메서드에서 이력을 로드해 시스템 프롬프트에 주입한 뒤 응답 후 저장한다. `buildSystemPromptWithHistory`는 package-private으로 독립 테스트 가능하게 한다.

**Tech Stack:** Java 25, Koog `AIAgent`, `JsonlConversationHistoryStorage` (Step 2 구현), JUnit 5

---

## File Map

| 작업 | 경로 |
|------|------|
| Modify | `src/main/java/koogagent/CodingAgent.java` |
| Create | `src/test/java/koogagent/CodingAgentHistoryTest.java` |

---

### Task 1: buildSystemPromptWithHistory 구현

이력을 시스템 프롬프트에 주입하는 헬퍼 메서드를 TDD로 구현한다. package-private으로 선언하여 테스트 가능하게 한다.

**Files:**
- Modify: `src/main/java/koogagent/CodingAgent.java`
- Create: `src/test/java/koogagent/CodingAgentHistoryTest.java`

- [ ] **Step 1: 실패하는 테스트 작성**

`src/test/java/koogagent/CodingAgentHistoryTest.java` 생성:

```java
package koogagent;

import ai.koog.prompt.message.Message;
import ai.koog.prompt.message.RequestMetaInfo;
import ai.koog.prompt.message.ResponseMetaInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CodingAgentHistoryTest {

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
}
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
mvn test -Dtest=CodingAgentHistoryTest -q 2>&1 | tail -5
```

Expected: FAIL — `buildSystemPromptWithHistory` 메서드 없음

- [ ] **Step 3: buildSystemPromptWithHistory 구현**

`CodingAgent.java`에 static package-private 메서드 추가 (기존 필드/생성자/chat 메서드는 유지):

```java
import ai.koog.prompt.message.Message;
import java.util.List;

// 클래스 내부에 추가
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
```

- [ ] **Step 4: 테스트 통과 확인**

```bash
mvn test -Dtest=CodingAgentHistoryTest -q 2>&1 | tail -5
```

Expected: BUILD SUCCESS, Tests run: 2, Failures: 0

- [ ] **Step 5: Commit**

```bash
git add src/main/java/koogagent/CodingAgent.java \
        src/test/java/koogagent/CodingAgentHistoryTest.java
git commit -m "feat: CodingAgent buildSystemPromptWithHistory 구현"
```

---

### Task 2: 생성자 오버로드 및 conversationHistoryStorage 필드 추가

`projectDir`/`sessionId`를 받는 전체 생성자와 기본값 생성자를 추가하고, `conversationHistoryStorage` 필드를 초기화한다.

**Files:**
- Modify: `src/main/java/koogagent/CodingAgent.java`
- Modify: `src/test/java/koogagent/CodingAgentHistoryTest.java`

- [ ] **Step 1: 실패하는 테스트 추가**

`CodingAgentHistoryTest.java`에 추가:

```java
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;

// 클래스 내부에 추가
@TempDir
Path tempDir;

@Test
void constructor_createsStorageDirectory() throws Exception {
    AnthropicLLMClient mockClient = Mockito.mock(AnthropicLLMClient.class);
    String projectDir = "test-project";
    String sessionId = "test-session";

    try (var agent = new CodingAgent(mockClient, "bash", projectDir, sessionId)) {
        Path expectedDir = Path.of(".koogagent/projects/" + projectDir + "/" + sessionId);
        assertThat(Files.exists(expectedDir)).isTrue();
        // 정리
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
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
mvn test -Dtest=CodingAgentHistoryTest#constructor_createsStorageDirectory -q 2>&1 | tail -5
```

Expected: FAIL — 4인자 생성자 없음

- [ ] **Step 3: 생성자 오버로드 및 필드 추가**

`CodingAgent.java` 전체를 아래 내용으로 교체:

```java
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
        AIAgent<String, String> agent = AIAgent.builder()
            .promptExecutor(executor)
            .llmModel(MODEL)
            .systemPrompt(SYSTEM_PROMPT)
            .temperature(0.3)
            .toolRegistry(toolRegistry)
            .maxIterations(MAX_ITERATIONS)
            .build();
        return agent.run(userMessage);
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
        if (client instanceof AutoCloseable ac) {
            ac.close();
        }
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

```bash
mvn test -Dtest=CodingAgentHistoryTest -q 2>&1 | tail -5
```

Expected: BUILD SUCCESS, Tests run: 3, Failures: 0

- [ ] **Step 5: Commit**

```bash
git add src/main/java/koogagent/CodingAgent.java \
        src/test/java/koogagent/CodingAgentHistoryTest.java
git commit -m "feat: CodingAgent 생성자 오버로드 및 conversationHistoryStorage 추가"
```

---

### Task 3: chat 메서드에 이력 로드/저장 통합

`chat` 호출 시 이력을 로드해 시스템 프롬프트에 주입하고, 응답 후 저장한다.

**Files:**
- Modify: `src/main/java/koogagent/CodingAgent.java`

- [ ] **Step 1: chat 메서드 수정**

`CodingAgent.java`의 `chat` 메서드를 아래와 같이 교체:

```java
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
```

- [ ] **Step 2: 전체 테스트 통과 확인**

```bash
mvn test -q 2>&1 | tail -8
```

Expected: BUILD SUCCESS, Tests run: 11, Failures: 0

- [ ] **Step 3: Commit**

```bash
git add src/main/java/koogagent/CodingAgent.java
git commit -m "feat: chat 메서드에 대화 이력 로드/저장 통합"
```
