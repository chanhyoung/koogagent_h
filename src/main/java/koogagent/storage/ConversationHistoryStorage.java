package koogagent.storage;

import java.io.IOException;
import java.util.List;

import ai.koog.prompt.message.Message;

public interface ConversationHistoryStorage {
  void addConversation(String userMessage, String assistantMessage) throws IOException;
  List<Message> getHistory() throws IOException;
}
