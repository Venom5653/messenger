package com.example.messengerservice.dto.chat;

import java.time.LocalDateTime;

public record ChatRoomResponse(

        Long id,

        Long user1Id,

        Long user2Id,

        String user1Username,

        String user2Username,

        String user1Avatar,

        String user2Avatar,

        String lastMessage,

        LocalDateTime lastMessageCreatedAt,

        long unreadCount,

        LocalDateTime createdAt

) {
}