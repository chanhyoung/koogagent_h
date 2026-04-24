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
