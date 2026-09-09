package com.example.messengerservice.dto.chat;

import com.example.messengerservice.entity.ChatType;

import java.time.LocalDateTime;
import java.util.List;

public record ChatResponse(

        Long id,

        ChatType type,

        String name,

        String avatar,

        Long createdBy,

        LocalDateTime lastMessageAt,

        LocalDateTime createdAt,

        String lastMessage,

        Long unreadCount,

        List<ChatMemberResponse> members

) {
}