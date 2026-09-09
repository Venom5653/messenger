package com.example.messengerservice.dto.messenges;

import java.time.LocalDateTime;

public record MessageResponse(

        Long id,

        Long chatId,

        Long senderId,

        String senderUsername,

        String senderAvatar,

        String content,

        LocalDateTime createdAt,

        boolean read

) {
}
