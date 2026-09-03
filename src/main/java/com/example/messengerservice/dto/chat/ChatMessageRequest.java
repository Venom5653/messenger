package com.example.messengerservice.dto.chat;

import jakarta.validation.constraints.NotBlank;

public record ChatMessageRequest(

        @NotBlank
        String recipientUsername,

        @NotBlank
        String content

) {
}