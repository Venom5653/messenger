package com.example.messengerservice.dto.websocket;

public record MessageDeletedEvent(
        Long messageId,
        Long chatId
) {
}
