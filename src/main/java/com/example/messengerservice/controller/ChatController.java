package com.example.messengerservice.controller;

import com.example.messengerservice.dto.chat.ChatResponse;
import com.example.messengerservice.dto.chat.CreatePrivateChatRequest;
import com.example.messengerservice.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chats")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping("/private")
    public ResponseEntity<ChatResponse> createPrivateChat(@Valid @RequestBody CreatePrivateChatRequest request, @RequestHeader("Authorization") String authorization, @RequestHeader("X-User-Id") Long currentUserId) {

        return ResponseEntity.ok(chatService.createPrivateChat(request, currentUserId, authorization));
    }

    @GetMapping
    public ResponseEntity<List<ChatResponse>> getMyChats(@RequestHeader("Authorization") String authorization, @RequestHeader("X-User-Id") Long currentUserId) {

        return ResponseEntity.ok(chatService.getMyChats(currentUserId, authorization));
    }

    @GetMapping("/{chatId}")
    public ResponseEntity<ChatResponse> getChat(@PathVariable Long chatId, @RequestHeader("Authorization") String authorization, @RequestHeader("X-User-Id") Long currentUserId) {

        return ResponseEntity.ok(chatService.getChat(chatId, currentUserId, authorization));
    }
}