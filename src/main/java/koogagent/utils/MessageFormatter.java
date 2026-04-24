package koogagent.utils;

import ai.koog.prompt.message.Message;

import java.util.List;

public class MessageFormatter {
    private MessageFormatter() {}

    public static void appendMessages(StringBuilder sb, List<Message> messages) {
        for (Message msg : messages) {
            if (msg instanceof Message.User u)
                sb.append("User: ").append(u.getContent()).append("\n");
            else if (msg instanceof Message.Assistant a)
                sb.append("Assistant: ").append(a.getContent()).append("\n");
        }
    }
}
