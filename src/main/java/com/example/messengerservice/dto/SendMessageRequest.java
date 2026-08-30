package com.example.messengerservice.dto;

import jakarta.validation.constraints.NotBlank;


public record SendMessageRequest(

        @NotBlank
        String recipientUsername,

        @NotBlank
        String content

) {
}
