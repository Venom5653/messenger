package com.example.messengerservice.controller;

import com.example.messengerservice.dto.messenges.MessageResponse;
import com.example.messengerservice.dto.messenges.SendMessageRequest;
import com.example.messengerservice.service.MessageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    @PostMapping
    public ResponseEntity<MessageResponse> sendMessage(
            @Valid @RequestBody SendMessageRequest request,
            Authentication authentication,
            @RequestHeader("Authorization") String authorization
    ) {

        return ResponseEntity.ok(
                messageService.sendMessage(
                        request,
                        authentication,
                        authorization
                )
        );
    }

    @GetMapping("/chat/{chatId}")
    public ResponseEntity<List<MessageResponse>> getChatMessages(
            @PathVariable Long chatId,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(defaultValue = "50") int limit,
            Authentication authentication,
            @RequestHeader("Authorization") String authorization
    ) {

        return ResponseEntity.ok(
                messageService.getChatMessages(
                        chatId,
                        beforeId,
                        limit,
                        authentication,
                        authorization
                )
        );
    }

    @PutMapping("/chat/{chatId}/read")
    public ResponseEntity<Void> markChatAsRead(
            @PathVariable Long chatId,
            Authentication authentication,
            @RequestHeader("Authorization") String authorization
    ) {

        messageService.markChatAsRead(
                chatId,
                authentication,
                authorization
        );

        return ResponseEntity.ok().build();
    }

    @GetMapping("/chat/{chatId}/unread")
    public ResponseEntity<Long> getUnreadCount(
            @PathVariable Long chatId,
            @RequestHeader("X-User-Id") Long currentUserId
    ) {

        return ResponseEntity.ok(
                messageService.getUnreadCount(
                        chatId,
                        currentUserId
                )
        );
    }
}