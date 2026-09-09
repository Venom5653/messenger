package com.example.messengerservice.dto.chat;

import com.example.messengerservice.entity.ChatMemberRole;

import java.time.LocalDateTime;

public record ChatMemberResponse(

        Long userId,

        String username,

        String avatar,

        ChatMemberRole role,

        LocalDateTime joinedAt,

        Long lastReadMessageId

) {
}
