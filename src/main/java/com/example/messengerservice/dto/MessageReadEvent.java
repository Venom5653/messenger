package com.example.messengerservice.dto;

public record MessageReadEvent(
        Long chatId,
        Long messageId,
        Long readerId
) {
}