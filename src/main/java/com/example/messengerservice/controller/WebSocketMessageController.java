package com.example.messengerservice.controller;

import com.example.messengerservice.dto.chat.ChatMessageRequest;
import com.example.messengerservice.dto.chat.ChatStateRequest;
import com.example.messengerservice.dto.messenges.MessageResponse;
import com.example.messengerservice.dto.messenges.SendMessageRequest;
import com.example.messengerservice.security.WebSocketAuthInterceptor;
import com.example.messengerservice.service.ActiveChatService;
import com.example.messengerservice.service.MessageService;
import com.example.messengerservice.service.NotificationEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class WebSocketMessageController {

    private final MessageService messageService;
    private final SimpMessagingTemplate messagingTemplate;
    private final SimpUserRegistry simpUserRegistry;
    private final ActiveChatService activeChatService;
    private final NotificationEventPublisher notificationEventPublisher;

    @MessageMapping("/chat")
    public void sendMessage(ChatMessageRequest request, Principal principal, StompHeaderAccessor accessor) {

        if (principal == null) {
            return;
        }
        System.out.println("=== WEBSOCKET /app/chat ПОЛУЧЕН ===");
        System.out.println("Principal: " + principal);
        System.out.println("Request: " + request);
        String senderUsername = principal.getName();

        String token = null;

        if (accessor.getSessionAttributes() != null) {

            Object jwt = accessor.getSessionAttributes().get(WebSocketAuthInterceptor.JWT_ATTRIBUTE);

            if (jwt instanceof String jwtToken) {
                token = jwtToken;
            }
        }

        if (principal == null) {
            System.out.println("❌ CHAT: Principal = null");
            return;
        }

        System.out.println("✅ CHAT: Principal = " + principal.getName());

        String authorization = "Bearer " + token;

        Authentication authentication = new UsernamePasswordAuthenticationToken(senderUsername, null, List.of());

        MessageResponse savedMessage = messageService.sendMessage(new SendMessageRequest(request.recipientUsername(), request.content()), authentication, authorization);

        System.out.println("SEND TO RECIPIENT: " + request.recipientUsername());
        System.out.println("SEND TO SENDER: " + senderUsername);
        System.out.println("WS USERS:");

        simpUserRegistry.getUsers().forEach(user -> System.out.println("USER = " + user.getName() + ", sessions = " + user.getSessions().size()));
        messagingTemplate.convertAndSendToUser(request.recipientUsername(), "/queue/messages", savedMessage);
        System.out.println("MESSAGE SENT TO RECIPIENT");
        messagingTemplate.convertAndSendToUser(senderUsername, "/queue/messages", savedMessage);
        System.out.println("MESSAGE SENT TO SENDER");
    }

    @MessageMapping("/chat/open")
    public void openChat(ChatStateRequest request, Principal principal) {

        if (principal == null) {
            return;
        }

        if (request == null || request.chatId() == null) {
            return;
        }

        String username = principal.getName();

        activeChatService.openChat(username, request.chatId());

        notificationEventPublisher.publishChatOpened(username, request.chatId());

        System.out.println("ACTIVE CHAT: " + username + " -> " + request.chatId());
    }

    @MessageMapping("/chat/close")
    public void closeChat(Principal principal) {

        if (principal == null) {
            return;
        }

        String username = principal.getName();

        Long chatId = activeChatService.getActiveChat(username);

        activeChatService.closeChat(username);

        if (chatId != null) {

            notificationEventPublisher.publishChatClosed(username, chatId);
        }

        System.out.println("ACTIVE CHAT CLOSED: " + username + ", chatId=" + chatId);
    }
}