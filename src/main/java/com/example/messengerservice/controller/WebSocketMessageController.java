package com.example.messengerservice.controller;

import com.example.messengerservice.client.UserClient;
import com.example.messengerservice.dto.UserProfileResponse;
import com.example.messengerservice.dto.chat.ChatMessageRequest;
import com.example.messengerservice.dto.chat.ChatStateRequest;
import com.example.messengerservice.dto.messenges.MessageResponse;
import com.example.messengerservice.dto.messenges.SendMessageRequest;
import com.example.messengerservice.entity.ChatMember;
import com.example.messengerservice.repository.ChatMemberRepository;
import com.example.messengerservice.security.WebSocketAuthInterceptor;
import com.example.messengerservice.service.ActiveChatService;
import com.example.messengerservice.service.MessageService;
import com.example.messengerservice.service.NotificationEventPublisher;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class WebSocketMessageController {

    private final MessageService messageService;

    private final ChatMemberRepository chatMemberRepository;

    private final UserClient userClient;

    private final SimpMessagingTemplate messagingTemplate;

    private final ActiveChatService activeChatService;

    private final NotificationEventPublisher notificationEventPublisher;


    // =====================================================
    // SEND MESSAGE
    // =====================================================

    @MessageMapping("/chat")
    public void sendMessage(ChatMessageRequest request, Principal principal, StompHeaderAccessor accessor) {

        if (principal == null) {

            System.out.println("❌ CHAT: Principal = null");

            return;
        }


        if (request == null) {

            System.out.println("❌ CHAT: request = null");

            return;
        }


        if (request.chatId() == null) {

            System.out.println("❌ CHAT: chatId отсутствует");

            return;
        }


        if (request.content() == null || request.content().isBlank()) {

            System.out.println("❌ CHAT: content пустой");

            return;
        }


        String senderUsername = principal.getName();


        String authorization = getAuthorization(accessor);


        if (authorization == null) {

            System.out.println("❌ CHAT: JWT отсутствует");

            return;
        }


        Authentication authentication = new UsernamePasswordAuthenticationToken(senderUsername, null, List.of());


        MessageResponse savedMessage = messageService.sendMessage(new SendMessageRequest(request.chatId(), request.content()), authentication, authorization);


        // =================================================
        // GET CHAT MEMBERS
        // =================================================

        List<ChatMember> members = chatMemberRepository.findAllByChatId(request.chatId());


        if (members.isEmpty()) {

            return;
        }


        // =================================================
        // COLLECT USER IDS
        // =================================================

        List<Long> userIds = members.stream().map(ChatMember::getUserId).distinct().toList();


        // =================================================
        // BATCH GET USERS
        // =================================================

        List<UserProfileResponse> users;

        try {

            users = userClient.getUsersByIds(userIds, authorization);

        } catch (FeignException e) {

            System.out.println("❌ Ошибка AuthService при batch-запросе пользователей: " + e.status());

            return;
        }


        // =================================================
        // USER ID → USER
        // =================================================

        Map<Long, UserProfileResponse> usersById = new HashMap<>();


        for (UserProfileResponse user : users) {

            if (user == null || user.id() == null) {

                continue;
            }

            usersById.put(user.id(), user);
        }


        // =================================================
        // WEBSOCKET BROADCAST
        // =================================================

        for (ChatMember member : members) {

            UserProfileResponse user = usersById.get(member.getUserId());


            if (user == null || user.username() == null || user.username().isBlank()) {

                continue;
            }


            messagingTemplate.convertAndSendToUser(user.username(), "/queue/messages", savedMessage);
        }
    }


    // =====================================================
    // OPEN CHAT
    // =====================================================

    @MessageMapping("/chat/open")
    public void openChat(ChatStateRequest request, Principal principal, StompHeaderAccessor accessor) {

        if (principal == null) {

            System.out.println("❌ CHAT OPEN: Principal = null");

            return;
        }


        if (request == null || request.chatId() == null) {

            System.out.println("❌ CHAT OPEN: chatId отсутствует");

            return;
        }


        String username = principal.getName();


        String authorization = getAuthorization(accessor);


        if (authorization == null) {

            System.out.println("❌ CHAT OPEN: JWT отсутствует");

            return;
        }


        UserProfileResponse currentUser;

        try {

            currentUser = userClient.getUserByUsername(username, authorization);

        } catch (FeignException.NotFound e) {

            System.out.println("❌ CHAT OPEN: пользователь не найден: " + username);

            return;

        } catch (FeignException e) {

            System.out.println("❌ CHAT OPEN: ошибка AuthService: " + e.status());

            return;
        }


        if (currentUser == null || currentUser.id() == null) {

            System.out.println("❌ CHAT OPEN: невозможно определить userId");

            return;
        }


        Long userId = currentUser.id();


        // =================================================
        // CHECK MEMBERSHIP
        // =================================================

        boolean isMember = chatMemberRepository.existsByChatIdAndUserId(request.chatId(), userId);


        if (!isMember) {

            System.out.println("⚠️ CHAT OPEN: пользователь " + username + " попытался открыть чужой чат " + request.chatId());

            return;
        }


        // =================================================
        // OPEN ACTIVE CHAT
        // =================================================

        activeChatService.openChat(username, request.chatId());


        // =================================================
        // NOTIFICATION SERVICE
        // =================================================

        notificationEventPublisher.publishChatOpened(username, request.chatId());


        System.out.println("ACTIVE CHAT: " + username + " -> " + request.chatId());
    }


    // =====================================================
    // CLOSE CHAT
    // =====================================================

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


    // =====================================================
    // GET AUTHORIZATION
    // =====================================================

    private String getAuthorization(StompHeaderAccessor accessor) {

        if (accessor == null) {

            return null;
        }


        if (accessor.getSessionAttributes() == null) {

            return null;
        }


        Object jwt = accessor.getSessionAttributes().get(WebSocketAuthInterceptor.JWT_ATTRIBUTE);


        if (!(jwt instanceof String jwtToken)) {

            return null;
        }


        if (jwtToken.isBlank()) {

            return null;
        }


        return "Bearer " + jwtToken;
    }
}
