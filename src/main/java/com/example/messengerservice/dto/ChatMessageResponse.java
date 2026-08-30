package com.example.messengerservice.dto;

import java.time.LocalDateTime;

public record ChatMessageResponse(
        Long id,
        String senderUsername,
        String recipientUsername,
        String content,
        LocalDateTime createdAt
) {
}