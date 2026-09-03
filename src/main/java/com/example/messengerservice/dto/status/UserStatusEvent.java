package com.example.messengerservice.dto.status;

public record UserStatusEvent(
        String username,
        boolean online
) {
}
