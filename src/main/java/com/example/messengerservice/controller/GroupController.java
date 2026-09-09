package com.example.messengerservice.controller;

import com.example.messengerservice.dto.chat.ChatResponse;
import com.example.messengerservice.dto.chat.CreateGroupChatRequest;
import com.example.messengerservice.dto.chat.UpdateGroupChatRequest;
import com.example.messengerservice.service.GroupChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chats")
@RequiredArgsConstructor
public class GroupController {

    private final GroupChatService groupChatService;

    @PostMapping("/group")
    public ResponseEntity<ChatResponse> createGroupChat(@Valid @RequestBody CreateGroupChatRequest request, @RequestHeader("Authorization") String authorization, @RequestHeader("X-User-Id") Long currentUserId) {

        return ResponseEntity.ok(groupChatService.createGroupChat(request, currentUserId, authorization));
    }

    @PutMapping("/{chatId}")
    public ResponseEntity<ChatResponse> updateGroup(@PathVariable Long chatId, @Valid @RequestBody UpdateGroupChatRequest request, @RequestHeader("Authorization") String authorization, @RequestHeader("X-User-Id") Long currentUserId) {

        return ResponseEntity.ok(groupChatService.updateGroup(chatId, request, currentUserId, authorization));
    }

    @DeleteMapping("/{chatId}")
    public ResponseEntity<Void> deleteGroup(@PathVariable Long chatId, @RequestHeader("Authorization") String authorization, @RequestHeader("X-User-Id") Long currentUserId) {

        groupChatService.deleteGroup(chatId, currentUserId, authorization);

        return ResponseEntity.noContent().build();
    }
}