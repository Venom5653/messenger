package com.example.messengerservice.controller;

import com.example.messengerservice.dto.chat.AddChatMemberRequest;
import com.example.messengerservice.dto.chat.ChangeMemberRoleRequest;
import com.example.messengerservice.dto.chat.ChatMemberResponse;
import com.example.messengerservice.service.GroupChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chats/{chatId}/members")
@RequiredArgsConstructor
public class GroupMemberController {

    private final GroupChatService groupChatService;

    @PostMapping
    public ResponseEntity<ChatMemberResponse> addMember(@PathVariable Long chatId, @Valid @RequestBody AddChatMemberRequest request, @RequestHeader("Authorization") String authorization, @RequestHeader("X-User-Id") Long currentUserId) {

        return ResponseEntity.ok(groupChatService.addMember(chatId, request, currentUserId, authorization));
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> removeMember(@PathVariable Long chatId, @PathVariable Long userId, @RequestHeader("Authorization") String authorization, @RequestHeader("X-User-Id") Long currentUserId) {

        groupChatService.removeMember(chatId, userId, currentUserId, authorization);

        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> leaveGroup(@PathVariable Long chatId, @RequestHeader("Authorization") String authorization, @RequestHeader("X-User-Id") Long currentUserId) {

        groupChatService.leaveGroup(chatId, currentUserId, authorization);

        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{userId}/role")
    public ResponseEntity<ChatMemberResponse> changeMemberRole(@PathVariable Long chatId, @PathVariable Long userId, @Valid @RequestBody ChangeMemberRoleRequest request, @RequestHeader("Authorization") String authorization, @RequestHeader("X-User-Id") Long currentUserId) {

        return ResponseEntity.ok(groupChatService.changeMemberRole(chatId, userId, request, currentUserId, authorization));
    }

    @PutMapping("/owner/{userId}")
    public ResponseEntity<Void> transferOwnership(@PathVariable Long chatId, @PathVariable Long userId, @RequestHeader("Authorization") String authorization, @RequestHeader("X-User-Id") Long currentUserId) {

        groupChatService.transferOwnership(chatId, userId, currentUserId, authorization);

        return ResponseEntity.noContent().build();
    }
}