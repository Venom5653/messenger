package com.example.messengerservice.dto.chat;

import com.example.messengerservice.entity.ChatMemberRole;
import jakarta.validation.constraints.NotNull;

public record ChangeMemberRoleRequest(

        @NotNull(message = "Роль обязательна")
        ChatMemberRole role
) {
}
