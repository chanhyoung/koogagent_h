package koogagent.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class KodingMemoryStorageTest {

    @TempDir
    Path tempDir;

    @Test
    void getMemory_returnsNull_whenFileDoesNotExist() throws Exception {
        var storage = new KodingMemoryStorage(tempDir.resolve("KODING.md"));

        assertThat(storage.getMemory()).isNull();
    }

    @Test
    void addMemory_createsFile_withListFormat() throws Exception {
        Path file = tempDir.resolve("KODING.md");
        var storage = new KodingMemoryStorage(file);

        storage.addMemory("이 프로젝트는 Java + Maven 기반이야");

        assertThat(Files.exists(file)).isTrue();
        assertThat(Files.readString(file)).isEqualTo("- 이 프로젝트는 Java + Maven 기반이야");
    }

    @Test
    void addMemory_appendsToExistingMemory() throws Exception {
        var storage = new KodingMemoryStorage(tempDir.resolve("KODING.md"));

        storage.addMemory("첫 번째 항목");
        storage.addMemory("두 번째 항목");

        String memory = storage.getMemory();
        assertThat(memory).isEqualTo("- 첫 번째 항목\n- 두 번째 항목");
    }

    @Test
    void getMemory_returnsNull_whenFileIsBlank() throws Exception {
        Path file = tempDir.resolve("KODING.md");
        Files.writeString(file, "   ");
        var storage = new KodingMemoryStorage(file);

        assertThat(storage.getMemory()).isNull();
    }
}
