package com.example.messengerservice.dto.messenges;

public record MessageSentEvent(
        String eventType,
        Long messageId,
        Long chatId,
        String senderUsername,
        String recipientUsername,
        String content
) {
}
