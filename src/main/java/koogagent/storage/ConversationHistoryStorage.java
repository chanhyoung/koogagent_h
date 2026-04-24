package koogagent.storage;

import java.util.List;

import ai.koog.prompt.message.Message;

public interface ConversationHistoryStorage {
  void addConversation(String userMessage, String assistantMessage);
  List<Message> getHistory();
}
