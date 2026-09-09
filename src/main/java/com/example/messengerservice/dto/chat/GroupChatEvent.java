package com.example.messengerservice.dto.chat;

import com.example.messengerservice.entity.ChatMemberRole;

public record GroupChatEvent(

        String eventType,

        Long chatId,

        Long actorUserId,

        Long targetUserId,

        ChatMemberRole role
) {
}