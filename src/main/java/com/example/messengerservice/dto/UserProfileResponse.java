package com.example.messengerservice.dto;

public record UserProfileResponse(
        Long id,
        String username,
        String avatar
) {
}