package com.example.messengerservice.dto.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record CreateGroupChatRequest(


        @NotBlank(message = "Название группы не может быть пустым") String name,


        @NotEmpty(message = "Добавьте хотя бы одного участника") List<Long> userIds


) {
}
