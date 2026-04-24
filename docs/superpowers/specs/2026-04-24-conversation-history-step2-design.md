# Step 2: JsonlConversationHistoryStorage 설계

## 개요

JSONL 파일에 대화 이력을 저장/조회하는 `JsonlConversationHistoryStorage` 구현.
kotlinx-serialization을 사용하여 `Message` 객체를 직렬화하고, Jackson으로 외부 래퍼를 처리한다.

## 파일

| 작업 | 경로 |
|------|------|
| Create | `src/main/java/koogagent/storage/JsonlConversationHistoryStorage.java` |
| Create | `src/test/java/koogagent/storage/JsonlConversationHistoryStorageTest.java` |

## JSONL 형식

각 줄 = 메시지 하나. user/assistant 각각 별도 줄.

```json
{"timestamp":"2026-04-24T00:00:00Z","type":"user","data":{"parts":[{"_type":"TextContent","text":"안녕"}],"metaInfo":{...}}}
{"timestamp":"2026-04-24T00:00:00Z","type":"assistant","data":{"parts":[...],"metaInfo":{...}}}
```

- `timestamp`: ISO-8601 Instant 문자열 (Jackson 직렬화)
- `type`: `"user"` | `"assistant"` (타입 판별자)
- `data`: kotlinx-serialization이 생성한 Message subtype JSON

## 클래스 설계

```java
public class JsonlConversationHistoryStorage implements ConversationHistoryStorage {
    private final Path historyFile;
    private final Json json;           // kotlinx-serialization Json
    private final ObjectMapper mapper; // Jackson (외부 래퍼용)
}
```

### 생성자

```java
public JsonlConversationHistoryStorage(Path sessionDir) throws IOException {
    Files.createDirectories(sessionDir);
    this.historyFile = sessionDir.resolve("session.jsonl");
    this.json = JsonKt.Json(Json.Default, cfg -> { cfg.setIgnoreUnknownKeys(true); return Unit.INSTANCE; });
    this.mapper = new ObjectMapper().registerModule(new JavaTimeModule());
}
```

## 직렬화 전략

kotlinx-serialization으로 Message subtype을 직렬화:

```java
// 쓰기
KSerializer<Message.User> ser = (KSerializer<Message.User>) SerializersKt.serializer(Message.User.class);
String dataJson = json.encodeToString(ser, message);

// 읽기 (type 필드 기반)
switch (type) {
    case "user" -> json.decodeFromString(SerializersKt.serializer(Message.User.class), data);
    case "assistant" -> json.decodeFromString(SerializersKt.serializer(Message.Assistant.class), data);
}
```

## addConversation 흐름

1. `new Message.User(userMessage, RequestMetaInfo.Companion.getEmpty())` 생성
2. kotlinx-serialization으로 data JSON 직렬화
3. Jackson ObjectNode로 `{timestamp, type, data}` 구성
4. 기존 파일 내용 읽기 (없으면 빈 문자열)
5. 새 줄 두 개(user + assistant) 추가 후 파일 쓰기

## getHistory 흐름

1. 파일 없으면 `emptyList()` 반환
2. 줄 단위 읽기, 빈 줄 제거
3. 각 줄 Jackson으로 파싱 → `type`, `data` 추출
4. type에 맞는 kotlinx-serialization serializer로 역직렬화
5. 파싱 실패 줄은 System.err 출력 후 null → mapNotNull로 제거

## 에러 처리

- 파일 I/O: `IOException` 그대로 전파 (인터페이스 시그니처에 `throws IOException` 추가)
- 손상된 JSONL 줄: 경고 로그 후 건너뜀

## 후속 단계

- Step 3~4: addConversation, getHistory 구현 (이번 step에 함께 포함)
- Step 5: CodingAgent에 통합
