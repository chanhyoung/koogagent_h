package koogagent.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class KodingMemoryStorage implements AgentMemoryStorage {

    private final Path memoryFile;

    public KodingMemoryStorage() {
        this(Path.of("./KODING.md"));
    }

    public KodingMemoryStorage(Path memoryFile) {
        this.memoryFile = memoryFile;
    }

    @Override
    public void addMemory(String content) throws IOException {
        String existing = getMemory();
        String line = "- " + content;
        String updated = (existing == null || existing.isBlank())
            ? line
            : existing + "\n" + line;
        Files.writeString(memoryFile, updated, StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING);
    }

    @Override
    public String getMemory() throws IOException {
        if (!Files.exists(memoryFile)) return null;
        String content = Files.readString(memoryFile);
        return content.isBlank() ? null : content;
    }
}
