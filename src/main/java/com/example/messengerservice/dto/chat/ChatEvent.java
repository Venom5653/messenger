package com.example.messengerservice.dto.chat;

public record ChatEvent(
        String eventType,
        Long chatId,
        Long userId
) {
}