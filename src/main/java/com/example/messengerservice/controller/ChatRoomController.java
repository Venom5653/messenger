package com.example.messengerservice.controller;

import com.example.messengerservice.dto.ChatRoomResponse;
import com.example.messengerservice.dto.CreateChatRequest;
import com.example.messengerservice.service.ChatRoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chats")
@RequiredArgsConstructor
public class ChatRoomController {

    private final ChatRoomService chatRoomService;

    @PostMapping
    public ResponseEntity<ChatRoomResponse> createChat(@RequestBody CreateChatRequest request, @RequestHeader("Authorization") String authorization, @RequestHeader("X-User-Id") Long currentUserId) {

        ChatRoomResponse response = chatRoomService.createChat(request, currentUserId, authorization);

        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<ChatRoomResponse>> getMyChats(@RequestHeader("Authorization") String authorization, @RequestHeader("X-User-Id") Long currentUserId) {

        List<ChatRoomResponse> chats = chatRoomService.getMyChats(currentUserId, authorization);

        return ResponseEntity.ok(chats);
    }
}