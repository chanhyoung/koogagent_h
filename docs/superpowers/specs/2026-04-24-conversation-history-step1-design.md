# Step 1: ConversationHistoryStorage 인터페이스 및 JsonlEntry 설계

## 개요

JSONL 형식으로 대화 이력을 저장/조회하기 위한 인터페이스와 데이터 모델 정의.
Kotlin 코드를 Java로 변환하여 기존 Spring Boot + Koog 프로젝트에 통합.

## 변경 범위

### ConversationHistoryStorage.java (수정)

- 패키지: `koogagent.storage`
- 오타 수정: `getHisotry()` → `getHistory()`
- 메서드는 동기(synchronous) — Kotlin `suspend fun` 대신 일반 Java 메서드

```java
public interface ConversationHistoryStorage {
    void addConversation(String userMessage, String assistantMessage);
    List<Message> getHistory();
}
```

### JsonlEntry.java (신규)

- 패키지: `koogagent.storage`
- Kotlin `data class` → Java `record` (Java 16+, 프로젝트는 Java 25)
- 필드: `Instant timestamp`, `Message message`

```java
public record JsonlEntry(Instant timestamp, Message message) {}
```

## 설계 결정

| 결정 | 이유 |
|------|------|
| Java record 사용 | Kotlin data class의 자연스러운 Java 대응체. 불변성 보장, 코드 최소화 |
| 동기 메서드 | Java에 suspend 개념 없음. Step 2 구현에서 동기 파일 I/O 사용 |
| `Instant` 타입 | Kotlin `Instant`(kotlinx-datetime)과 동일한 의미의 `java.time.Instant` 사용 |

## 후속 단계

- Step 2: `JsonlConversationHistoryStorage` 구현 (JSONL 파일 읽기/쓰기)
- Step 3~4: `addConversation`, `getHistory` 메서드 구현
- Step 5: `CodingAgent`에 통합
