package com.example.messengerservice.dto.chat;

import jakarta.validation.constraints.NotBlank;

public record CreatePrivateChatRequest(

        @NotBlank(message = "Username не может быть пустым")
        String username

) {
}
