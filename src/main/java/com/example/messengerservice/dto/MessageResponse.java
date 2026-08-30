package com.example.messengerservice.dto;

import java.time.LocalDateTime;

public record MessageResponse(

        Long id,

        Long chatId,

        String senderUsername,

        String recipientUsername,

        String content,

        LocalDateTime createdAt,

        Boolean read

) {
}