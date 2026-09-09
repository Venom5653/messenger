package com.example.messengerservice.dto.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateGroupChatRequest(

        @NotBlank(
                message = "Название группы не может быть пустым"
        )
        @Size(max = 255, message = "Название группы не может превышать 255 символов")
        String name

) {
}