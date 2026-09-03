package com.example.messengerservice.dto.messenges;

public record MessageReadEvent(
        Long chatId,
        Long messageId,
        Long readerId
) {
}