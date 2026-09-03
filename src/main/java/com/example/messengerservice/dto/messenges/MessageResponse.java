package com.example.messengerservice.dto.messenges;

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