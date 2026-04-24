# Conversation History Step 2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `JsonlConversationHistoryStorage`를 구현하여 JSONL 파일에 대화 이력을 저장/조회한다.

**Architecture:** kotlinx-serialization으로 `Message` 객체를 직렬화하고, Jackson으로 외부 래퍼(timestamp·type·data)를 처리한다. 파일 I/O는 `java.nio.file.Files`를 사용한다. 각 대화 턴은 JSONL 두 줄(user + assistant)로 저장된다.

**Tech Stack:** Java 25, kotlinx-serialization-json 1.8.1, Jackson (spring-boot-starter-web 포함), JUnit 5, `java.nio.file.Files`

---

## File Map

| 작업 | 경로 |
|------|------|
| Modify | `src/main/java/koogagent/storage/ConversationHistoryStorage.java` |
| Create | `src/main/java/koogagent/storage/JsonlConversationHistoryStorage.java` |
| Create | `src/test/java/koogagent/storage/JsonlConversationHistoryStorageTest.java` |

---

### Task 1: ConversationHistoryStorage 인터페이스에 throws IOException 추가

파일 I/O를 다루는 구현체가 checked exception을 명시적으로 선언할 수 있도록 인터페이스를 수정한다.

**Files:**
- Modify: `src/main/java/koogagent/storage/ConversationHistoryStorage.java`

- [ ] **Step 1: 인터페이스 수정**

```java
package koogagent.storage;

import java.io.IOException;
import java.util.List;

import ai.koog.prompt.message.Message;

public interface ConversationHistoryStorage {
    void addConversation(String userMessage, String assistantMessage) throws IOException;
    List<Message> getHistory() throws IOException;
}
```

- [ ] **Step 2: 빌드 확인**

```bash
mvn compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/koogagent/storage/ConversationHistoryStorage.java
git commit -m "feat: ConversationHistoryStorage에 throws IOException 추가"
```

---

### Task 2: JsonlConversationHistoryStorage 뼈대 + 생성자 구현

디렉토리 생성과 의존성 초기화를 담당하는 생성자를 TDD로 구현한다.

**Files:**
- Create: `src/main/java/koogagent/storage/JsonlConversationHistoryStorage.java`
- Create: `src/test/java/koogagent/storage/JsonlConversationHistoryStorageTest.java`

- [ ] **Step 1: 실패하는 테스트 작성**

```java
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
}
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
mvn test -Dtest=JsonlConversationHistoryStorageTest#constructor_createsSessionDirectory -q 2>&1 | tail -5
```

Expected: FAIL — `JsonlConversationHistoryStorage` 클래스 없음

- [ ] **Step 3: 뼈대 클래스 구현**

```java
package koogagent.storage;

import ai.koog.prompt.message.Message;
import com.fasterxml.jackson.databind.ObjectMapper;
import kotlin.Unit;
import kotlinx.serialization.KSerializer;
import kotlinx.serialization.json.Json;
import kotlinx.serialization.json.JsonKt;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
```

- [ ] **Step 4: 테스트 통과 확인**

```bash
mvn test -Dtest=JsonlConversationHistoryStorageTest#constructor_createsSessionDirectory -q 2>&1 | tail -5
```

Expected: BUILD SUCCESS, Tests run: 1, Failures: 0

- [ ] **Step 5: Commit**

```bash
git add src/main/java/koogagent/storage/JsonlConversationHistoryStorage.java \
        src/test/java/koogagent/storage/JsonlConversationHistoryStorageTest.java
git commit -m "feat: JsonlConversationHistoryStorage 뼈대 및 생성자 구현"
```

---

### Task 3: addConversation 구현

user/assistant 메시지를 JSONL 두 줄로 직렬화하여 파일에 추가한다.

**JSONL 줄 형식:**
```json
{"timestamp":"2026-04-24T00:00:00Z","type":"user","data":{...Message.User kotlinx JSON...}}
```

**Files:**
- Modify: `src/main/java/koogagent/storage/JsonlConversationHistoryStorage.java`
- Modify: `src/test/java/koogagent/storage/JsonlConversationHistoryStorageTest.java`

- [ ] **Step 1: 실패하는 테스트 추가**

`JsonlConversationHistoryStorageTest`에 아래 테스트를 추가한다:

```java
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
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
mvn test -Dtest=JsonlConversationHistoryStorageTest#addConversation_createsFileWithTwoLines -q 2>&1 | tail -5
```

Expected: FAIL — `UnsupportedOperationException`

- [ ] **Step 3: addConversation 구현**

`JsonlConversationHistoryStorage.java`에서 `addConversation` 메서드와 `buildLine` 헬퍼를 구현한다:

```java
import ai.koog.prompt.message.RequestMetaInfo;
import ai.koog.prompt.message.ResponseMetaInfo;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;

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

private String buildLine(String type, String dataJson) throws IOException {
    ObjectNode node = mapper.createObjectNode();
    node.put("timestamp", Instant.now().toString());
    node.put("type", type);
    node.set("data", mapper.readTree(dataJson));
    return mapper.writeValueAsString(node);
}
```

전체 import 목록 (파일 상단):

```java
import ai.koog.prompt.message.Message;
import ai.koog.prompt.message.RequestMetaInfo;
import ai.koog.prompt.message.ResponseMetaInfo;
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
import java.util.List;
```

- [ ] **Step 4: 테스트 통과 확인**

```bash
mvn test -Dtest=JsonlConversationHistoryStorageTest -q 2>&1 | tail -8
```

Expected: BUILD SUCCESS, Tests run: 3, Failures: 0

- [ ] **Step 5: Commit**

```bash
git add src/main/java/koogagent/storage/JsonlConversationHistoryStorage.java \
        src/test/java/koogagent/storage/JsonlConversationHistoryStorageTest.java
git commit -m "feat: addConversation JSONL 저장 구현"
```

---

### Task 4: getHistory 구현

JSONL 파일을 줄 단위로 읽고 `Message` 객체 목록을 반환한다.

**Files:**
- Modify: `src/main/java/koogagent/storage/JsonlConversationHistoryStorage.java`
- Modify: `src/test/java/koogagent/storage/JsonlConversationHistoryStorageTest.java`

- [ ] **Step 1: 실패하는 테스트 추가**

```java
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
    // 손상된 줄 삽입
    Files.writeString(sessionDir.resolve("session.jsonl"),
        Files.readString(sessionDir.resolve("session.jsonl")) + "\n손상된줄");

    var history = storage.getHistory();

    assertThat(history).hasSize(2); // 손상된 줄 건너뜀
}
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
mvn test -Dtest=JsonlConversationHistoryStorageTest#getHistory_returnsEmptyWhenNoFile -q 2>&1 | tail -5
```

Expected: FAIL — `UnsupportedOperationException`

- [ ] **Step 3: getHistory 구현**

`JsonlConversationHistoryStorage.java`에서 `getHistory` 메서드를 구현한다:

```java
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;

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
```

- [ ] **Step 4: 전체 테스트 통과 확인**

```bash
mvn test -Dtest=JsonlConversationHistoryStorageTest -q 2>&1 | tail -8
```

Expected: BUILD SUCCESS, Tests run: 7, Failures: 0

- [ ] **Step 5: Commit**

```bash
git add src/main/java/koogagent/storage/JsonlConversationHistoryStorage.java \
        src/test/java/koogagent/storage/JsonlConversationHistoryStorageTest.java
git commit -m "feat: getHistory JSONL 읽기 및 역직렬화 구현"
```
