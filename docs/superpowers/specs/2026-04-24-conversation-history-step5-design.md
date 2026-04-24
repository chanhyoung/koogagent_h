# Step 5: CodingAgent 대화 이력 통합 설계

## 개요

`CodingAgent`에 `JsonlConversationHistoryStorage`를 통합하여 세션 간 대화 이력을 유지한다.

## 변경 파일

| 작업 | 경로 |
|------|------|
| Modify | `src/main/java/koogagent/CodingAgent.java` |
| Modify | `src/main/java/koogagent/KoogAgentMain.java` |
| Create | `src/test/java/koogagent/CodingAgentHistoryTest.java` |

## CodingAgent 변경

### 생성자 오버로드

Java 기본 매개변수 대신 오버로드 사용. 기존 `KoogAgentMain` 호환 유지.

```java
// 기존 호출부 호환 (projectDir=default, sessionId=UUID 자동)
public CodingAgent(AnthropicLLMClient client, String bashPath) throws IOException {
    this(client, bashPath, "default", UUID.randomUUID().toString());
}

// 전체 생성자
public CodingAgent(AnthropicLLMClient client, String bashPath, String projectDir, String sessionId) throws IOException
```

저장 경로: `.koogagent/projects/{projectDir}/{sessionId}/session.jsonl`

### 필드 추가

```java
private final ConversationHistoryStorage conversationHistoryStorage;
```

### chat 메서드 변경

1. `conversationHistoryStorage.getHistory()`로 이력 로드
2. `buildSystemPromptWithHistory(history)`로 시스템 프롬프트 구성
3. agent 실행
4. `conversationHistoryStorage.addConversation(userMessage, response)`로 저장

### buildSystemPromptWithHistory 헬퍼

이력이 없으면 기존 `SYSTEM_PROMPT` 그대로 반환.  
이력이 있으면:
```
# System Prompt
{SYSTEM_PROMPT}

# Conversation History
User: ...
Assistant: ...

위의 맥락을 바탕으로 대화를 이어가 주세요.
```

## KoogAgentMain 변경

`CodingAgent` 생성자가 `IOException`을 던지므로 `main` 메서드의 `throws Exception` 선언으로 처리 (기존에 이미 선언되어 있음).

## 에러 처리

- `IOException`은 `chat` 메서드의 `throws Exception`으로 전파
- 이력 로드/저장 실패 시 대화 자체는 계속 진행되지 않음 (IOException 전파)
