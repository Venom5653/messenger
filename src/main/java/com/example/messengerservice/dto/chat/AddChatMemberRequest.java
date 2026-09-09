package com.example.messengerservice.dto.chat;

import jakarta.validation.constraints.NotNull;

public record AddChatMemberRequest(

        @NotNull(message = "ID пользователя обязателен")
        Long userId

) {
}