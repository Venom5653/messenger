package com.example.messengerservice.dto.messenges;

import jakarta.validation.constraints.NotBlank;


public record SendMessageRequest(

        @NotBlank
        String recipientUsername,

        @NotBlank
        String content

) {
}
