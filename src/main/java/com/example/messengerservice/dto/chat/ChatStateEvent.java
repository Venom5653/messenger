package com.example.messengerservice.dto.chat;

public record ChatStateEvent(
        String eventType,
        String username,
        Long chatId
) {
}