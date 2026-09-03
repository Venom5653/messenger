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
    public MessageResponse sendMessage(@Valid @RequestBody SendMessageRequest request, Authentication authentication, @RequestHeader("Authorization") String authorization) {

        return messageService.sendMessage(request, authentication, authorization);
    }

    @GetMapping("/conversation/{username}")
    public List<MessageResponse> getConversation(@PathVariable String username, Authentication authentication, @RequestHeader("Authorization") String authorization) {

        return messageService.getConversation(username, authentication, authorization);
    }

    @GetMapping("/chat/{chatId}")
    public List<MessageResponse> getChatMessages(@PathVariable Long chatId,

                                                 @RequestParam(required = false) Long beforeId,

                                                 @RequestParam(defaultValue = "50") int limit,

                                                 Authentication authentication,

                                                 @RequestHeader("Authorization") String authorization) {

        return messageService.getChatMessages(chatId, beforeId, limit, authentication, authorization);
    }

    @PutMapping("/chat/{chatId}/read")
    public ResponseEntity<Void> markChatAsRead(
            @PathVariable Long chatId, Authentication authentication,
            @RequestHeader("Authorization") String authorization) {

        messageService.markChatAsRead(chatId, authentication, authorization);

        return ResponseEntity.ok().build();
    }
}