package com.example.messengerservice.dto.websocket;

public record MessageReadEvent(
        Long chatId,
        Long messageId,
        Long readerId
) {
}