# Conversation History Step 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `ConversationHistoryStorage` 인터페이스 오타 수정 및 `JsonlEntry` Java record 신규 생성.

**Architecture:** 기존 인터페이스의 메서드명 오타를 수정하고, JSONL 저장 항목을 표현하는 불변 데이터 클래스를 Java record로 추가한다. 직렬화 구현은 Step 2에서 다룬다.

**Tech Stack:** Java 25 (record), `ai.koog.prompt.message.Message`, `java.time.Instant`, JUnit 5 (spring-boot-starter-test), Mockito

---

## File Map

| 작업 | 파일 경로 |
|------|-----------|
| Modify | `src/main/java/koogagent/storage/ConversationHistoryStorage.java` |
| Create | `src/main/java/koogagent/storage/JsonlEntry.java` |
| Create | `src/test/java/koogagent/storage/JsonlEntryTest.java` |

---

### Task 1: ConversationHistoryStorage 인터페이스 오타 수정

**Files:**
- Modify: `src/main/java/koogagent/storage/ConversationHistoryStorage.java`

- [ ] **Step 1: 오타 수정**

`getHisotry()` → `getHistory()` 로 수정. 파일 전체 내용:

```java
package koogagent.storage;

import java.util.List;

import ai.koog.prompt.message.Message;

public interface ConversationHistoryStorage {
    void addConversation(String userMessage, String assistantMessage);
    List<Message> getHistory();
}
```

- [ ] **Step 2: 빌드 확인**

```bash
mvn compile -q
```

Expected: BUILD SUCCESS (오타로 인한 기존 참조가 없으므로 컴파일 에러 없음)

- [ ] **Step 3: Commit**

```bash
git add src/main/java/koogagent/storage/ConversationHistoryStorage.java
git commit -m "fix: ConversationHistoryStorage getHisotry 오타 수정"
```

---

### Task 2: JsonlEntry Java record 생성

**Files:**
- Create: `src/main/java/koogagent/storage/JsonlEntry.java`
- Create: `src/test/java/koogagent/storage/JsonlEntryTest.java`

- [ ] **Step 1: 실패하는 테스트 작성**

`src/test/java/koogagent/storage/JsonlEntryTest.java` 생성:

```java
package koogagent.storage;

import ai.koog.prompt.message.Message;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class JsonlEntryTest {

    @Test
    void shouldStoreTimestampAndMessage() {
        Instant now = Instant.parse("2026-04-24T00:00:00Z");
        Message message = Mockito.mock(Message.class);

        JsonlEntry entry = new JsonlEntry(now, message);

        assertThat(entry.timestamp()).isEqualTo(now);
        assertThat(entry.message()).isSameAs(message);
    }

    @Test
    void shouldBeEqualWhenFieldsAreEqual() {
        Instant now = Instant.parse("2026-04-24T00:00:00Z");
        Message message = Mockito.mock(Message.class);

        JsonlEntry entry1 = new JsonlEntry(now, message);
        JsonlEntry entry2 = new JsonlEntry(now, message);

        assertThat(entry1).isEqualTo(entry2);
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
mvn test -pl . -Dtest=JsonlEntryTest -q 2>&1 | tail -5
```

Expected: FAIL — `JsonlEntry` 클래스가 없어서 컴파일 에러

- [ ] **Step 3: JsonlEntry record 구현**

`src/main/java/koogagent/storage/JsonlEntry.java` 생성:

```java
package koogagent.storage;

import ai.koog.prompt.message.Message;
import java.time.Instant;

public record JsonlEntry(Instant timestamp, Message message) {}
```

- [ ] **Step 4: 테스트 통과 확인**

```bash
mvn test -Dtest=JsonlEntryTest -q
```

Expected: BUILD SUCCESS, Tests run: 2, Failures: 0

- [ ] **Step 5: Commit**

```bash
git add src/main/java/koogagent/storage/JsonlEntry.java \
        src/test/java/koogagent/storage/JsonlEntryTest.java
git commit -m "feat: JsonlEntry Java record 추가 (대화 이력 저장 단위)"
```
