package koogagent.storage;

import ai.koog.prompt.message.Message;
import java.time.Instant;

public record JsonlEntry(Instant timestamp, Message message) {}
