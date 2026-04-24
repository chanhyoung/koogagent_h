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
