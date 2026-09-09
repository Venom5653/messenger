package com.example.messengerservice.dto.messenges;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SendMessageRequest(

        @NotNull(
                message = "Chat ID обязателен"
        )
        Long chatId,


        @NotBlank(
                message = "Сообщение не может быть пустым"
        )
        String content
) {
}
