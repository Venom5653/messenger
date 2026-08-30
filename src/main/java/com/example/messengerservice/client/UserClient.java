package com.example.messengerservice.client;

import com.example.messengerservice.config.FeignConfig;
import com.example.messengerservice.dto.UserProfileResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(
        name = "auth-service",
        url = "${auth-service.url}",
        configuration = FeignConfig.class
)
public interface UserClient {

    @GetMapping("/api/users/internal/{username}")
    UserProfileResponse getUserByUsername(

            @PathVariable("username")
            String username,

            @RequestHeader("Authorization")
            String authorization
    );

    @GetMapping("/api/users/internal/id/{id}")
    UserProfileResponse getUserById(

            @PathVariable("id")
            Long id,

            @RequestHeader("Authorization")
            String authorization
    );
}