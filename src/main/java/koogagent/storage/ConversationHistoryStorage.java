package koogagent.storage;

import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor;
import ai.koog.prompt.llm.LLModel;
import ai.koog.prompt.message.Message;

import java.io.IOException;
import java.util.List;

public interface ConversationHistoryStorage {
    void addConversation(String userMessage, String assistantMessage) throws IOException;
    List<Message> getHistory() throws IOException;
    String getSummary() throws IOException;
    void compressHistory(MultiLLMPromptExecutor executor, LLModel model) throws Exception;
}
