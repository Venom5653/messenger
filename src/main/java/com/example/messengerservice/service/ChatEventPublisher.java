package com.example.messengerservice.service;

import com.example.messengerservice.client.UserClient;
import com.example.messengerservice.dto.UserProfileResponse;
import com.example.messengerservice.dto.chat.GroupChatEvent;
import com.example.messengerservice.entity.ChatMember;
import com.example.messengerservice.repository.ChatMemberRepository;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ChatEventPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    private final ChatMemberRepository chatMemberRepository;

    private final UserClient userClient;


    // =====================================================
    // PUBLISH EVENT TO ALL CHAT MEMBERS
    // =====================================================

    public void publishToChatMembers(
            Long chatId,
            GroupChatEvent event,
            String authorization
    ) {

        publishToChatMembers(
                chatId,
                event,
                authorization,
                null
        );
    }


    // =====================================================
    // PUBLISH EVENT TO ALL CHAT MEMBERS
    // EXCEPT ONE USER
    // =====================================================

    public void publishToChatMembers(
            Long chatId,
            GroupChatEvent event,
            String authorization,
            Long excludedUserId
    ) {

        List<ChatMember> members =
                chatMemberRepository.findAllByChatId(
                        chatId
                );

        if (members.isEmpty()) {
            return;
        }


        // =================================================
        // COLLECT USER IDS
        // =================================================

        List<Long> userIds =
                members.stream()
                        .map(ChatMember::getUserId)
                        .filter(userId ->
                                excludedUserId == null ||
                                        !excludedUserId.equals(userId)
                        )
                        .distinct()
                        .toList();

        if (userIds.isEmpty()) {
            return;
        }


        // =================================================
        // BATCH LOAD USERS
        // =================================================

        List<UserProfileResponse> users;

        try {

            users =
                    userClient.getUsersByIds(
                            userIds,
                            authorization
                    );

        } catch (FeignException e) {

            System.out.println(
                    "❌ Ошибка AuthService при batch-запросе пользователей: "
                            + e.status()
            );

            return;
        }


        // =================================================
        // USER ID -> USER
        // =================================================

        Map<Long, UserProfileResponse> usersById =
                new HashMap<>();

        for (UserProfileResponse user : users) {

            if (user == null ||
                    user.id() == null) {

                continue;
            }

            usersById.put(
                    user.id(),
                    user
            );
        }


        // =================================================
        // SEND EVENT
        // =================================================

        for (ChatMember member : members) {

            Long userId =
                    member.getUserId();

            if (excludedUserId != null &&
                    excludedUserId.equals(userId)) {

                continue;
            }


            UserProfileResponse user =
                    usersById.get(userId);

            if (user == null ||
                    user.username() == null ||
                    user.username().isBlank()) {

                continue;
            }


            messagingTemplate.convertAndSendToUser(
                    user.username(),
                    "/queue/chat-events",
                    event
            );
        }
    }


    // =====================================================
    // PUBLISH EVENT TO ONE USER
    // =====================================================

    public void publishToUser(
            Long userId,
            GroupChatEvent event,
            String authorization
    ) {

        try {

            UserProfileResponse user =
                    userClient.getUserById(
                            userId,
                            authorization
                    );

            if (user == null ||
                    user.username() == null ||
                    user.username().isBlank()) {

                return;
            }

            messagingTemplate.convertAndSendToUser(
                    user.username(),
                    "/queue/chat-events",
                    event
            );

        } catch (FeignException.NotFound e) {

            System.out.println(
                    "⚠️ Пользователь не найден: "
                            + userId
            );

        } catch (FeignException e) {

            System.out.println(
                    "❌ AuthService error for user "
                            + userId
                            + ": "
                            + e.status()
            );
        }
    }
}