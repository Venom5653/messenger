package com.example.messengerservice.service;

import com.example.messengerservice.client.UserClient;
import com.example.messengerservice.dto.ChatRoomResponse;
import com.example.messengerservice.dto.CreateChatRequest;
import com.example.messengerservice.dto.UserProfileResponse;
import com.example.messengerservice.entity.ChatRoom;
import com.example.messengerservice.entity.Message;
import com.example.messengerservice.exception.UserNotFoundException;
import com.example.messengerservice.repository.ChatRoomRepository;
import com.example.messengerservice.repository.MessageRepository;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatRoomService {

    private final ChatRoomRepository chatRoomRepository;
    private final MessageRepository messageRepository;
    private final UserClient userClient;

    @Transactional
    public ChatRoomResponse createChat(CreateChatRequest request, Long currentUserId, String authorization) {

        String otherUsername = request.username();

        if (otherUsername == null || otherUsername.isBlank()) {

            throw new IllegalArgumentException("Username не может быть пустым");
        }

        otherUsername = otherUsername.trim();

        UserProfileResponse otherUser;

        try {

            System.out.println("Проверяем пользователя в AuthService: " + otherUsername);

            otherUser = userClient.getUserByUsername(otherUsername, authorization);

        } catch (FeignException.NotFound e) {

            System.out.println("Пользователь не найден: " + otherUsername);

            throw new UserNotFoundException(otherUsername);

        } catch (FeignException e) {

            System.out.println("Ошибка AuthService: " + e.status());

            throw new IllegalStateException("Не удалось проверить пользователя");
        }


        Long otherUserId = otherUser.id();

        if (currentUserId.equals(otherUserId)) {

            throw new IllegalArgumentException("Нельзя создать чат с самим собой");
        }

        Long user1Id;
        Long user2Id;

        if (currentUserId.compareTo(otherUserId) < 0) {

            user1Id = currentUserId;
            user2Id = otherUserId;

        } else {

            user1Id = otherUserId;
            user2Id = currentUserId;
        }

        ChatRoom chatRoom = chatRoomRepository.findByUser1IdAndUser2Id(user1Id, user2Id).orElseGet(() -> {

            ChatRoom newChat = ChatRoom.builder().user1Id(user1Id).user2Id(user2Id).build();

            return chatRoomRepository.save(newChat);
        });


        return toResponse(chatRoom, authorization, currentUserId);
    }

    @Transactional(readOnly = true)
    public List<ChatRoomResponse> getMyChats(Long currentUserId, String authorization) {

        return chatRoomRepository.findByUser1IdOrUser2Id(currentUserId, currentUserId).stream().map(chatRoom -> toResponse(chatRoom, authorization, currentUserId)).sorted((chat1, chat2) -> {

            LocalDateTime date1 = chat1.lastMessageCreatedAt();

            LocalDateTime date2 = chat2.lastMessageCreatedAt();

            if (date1 == null && date2 == null) {
                return 0;
            }

            if (date1 == null) {
                return 1;
            }

            if (date2 == null) {
                return -1;
            }

            return date2.compareTo(date1);
        }).toList();
    }


    private ChatRoomResponse toResponse(
            ChatRoom chatRoom,
            String authorization,
            Long currentUserId
    ) {

        UserProfileResponse user1 =
                getUserOrDeleted(
                        chatRoom.getUser1Id(),
                        authorization
                );

        UserProfileResponse user2 =
                getUserOrDeleted(
                        chatRoom.getUser2Id(),
                        authorization
                );

        Message lastMessage =
                messageRepository
                        .findTopByChatRoomIdOrderByCreatedAtDesc(
                                chatRoom.getId()
                        )
                        .orElse(null);

        long unreadCount =
                messageRepository
                        .countByChatRoomIdAndRecipientIdAndReadFalse(
                                chatRoom.getId(),
                                currentUserId
                        );

        return new ChatRoomResponse(

                chatRoom.getId(),

                chatRoom.getUser1Id(),
                chatRoom.getUser2Id(),

                user1.username(),
                user2.username(),

                user1.avatar(),
                user2.avatar(),

                lastMessage != null
                        ? lastMessage.getContent()
                        : null,

                lastMessage != null
                        ? lastMessage.getCreatedAt()
                        : null,

                unreadCount,

                chatRoom.getCreatedAt()
        );
    }

    private UserProfileResponse getUserOrDeleted(
            Long userId,
            String authorization
    ) {

        try {

            return userClient.getUserById(
                    userId,
                    authorization
            );

        } catch (FeignException.NotFound e) {

            return new UserProfileResponse(
                    userId,
                    "Удалённый пользователь",
                    null
            );
        }
    }
}