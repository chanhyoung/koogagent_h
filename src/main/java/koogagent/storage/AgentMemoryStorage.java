package koogagent.storage;

import java.io.IOException;

public interface AgentMemoryStorage {
    void addMemory(String content) throws IOException;
    String getMemory() throws IOException;
}
