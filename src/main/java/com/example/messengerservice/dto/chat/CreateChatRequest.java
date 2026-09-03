package com.example.messengerservice.dto.chat;

import jakarta.validation.constraints.NotBlank;

public record CreateChatRequest(

        @NotBlank
        String username

) {
}