package com.example.messengerservice.service;

import com.example.messengerservice.client.UserClient;
import com.example.messengerservice.dto.messenges.MessageReadEvent;
import com.example.messengerservice.dto.messenges.MessageResponse;
import com.example.messengerservice.dto.messenges.MessageSentEvent;
import com.example.messengerservice.dto.messenges.SendMessageRequest;
import com.example.messengerservice.dto.UserProfileResponse;
import com.example.messengerservice.entity.ChatRoom;
import com.example.messengerservice.entity.Message;
import com.example.messengerservice.exception.UserNotFoundException;
import com.example.messengerservice.repository.ChatRoomRepository;
import com.example.messengerservice.repository.MessageRepository;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageRepository messageRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final UserClient userClient;
    private final SimpMessagingTemplate messagingTemplate;
    private final NotificationEventPublisher notificationEventPublisher;

    @Transactional
    public MessageResponse sendMessage(SendMessageRequest request, Authentication authentication, String authorization) {

        String senderUsername = authentication.getName();


        UserProfileResponse sender;

        try {

            sender = userClient.getUserByUsername(senderUsername, authorization);

        } catch (FeignException.NotFound e) {

            throw new UserNotFoundException(senderUsername);

        } catch (FeignException e) {

            throw new IllegalStateException("Не удалось получить отправителя");
        }


        Long senderId = sender.id();


        String recipientUsername = request.recipientUsername();


        UserProfileResponse recipient;

        try {

            recipient = userClient.getUserByUsername(recipientUsername, authorization);

        } catch (FeignException.NotFound e) {

            throw new UserNotFoundException(recipientUsername);

        } catch (FeignException e) {

            throw new IllegalStateException("Не удалось получить получателя");
        }


        Long recipientId = recipient.id();


        if (senderId.equals(recipientId)) {

            throw new IllegalArgumentException("Нельзя отправить сообщение самому себе");
        }

        Long user1Id;
        Long user2Id;


        if (senderId.compareTo(recipientId) < 0) {

            user1Id = senderId;
            user2Id = recipientId;

        } else {

            user1Id = recipientId;
            user2Id = senderId;
        }


        ChatRoom chatRoom = chatRoomRepository.findByUser1IdAndUser2Id(user1Id, user2Id).orElseGet(() -> {

            ChatRoom newChat = ChatRoom.builder().user1Id(user1Id).user2Id(user2Id).build();

            return chatRoomRepository.save(newChat);
        });


        Message message = Message.builder()
                .senderId(senderId)
                .recipientId(recipientId)
                .content(request.content())
                .chatRoom(chatRoom)
                .build();

        Message savedMessage = messageRepository.save(message);

        MessageSentEvent event = new MessageSentEvent(
                "MESSAGE_SENT",
                savedMessage.getId(),
                chatRoom.getId(),
                senderUsername,
                recipientUsername,
                savedMessage.getContent()
        );

        notificationEventPublisher.publishMessageSent(event);

        return toResponse(savedMessage, senderUsername, recipientUsername);
    }

    @Transactional(readOnly = true)
    public List<MessageResponse> getConversation(String username, Authentication authentication, String authorization) {

        Long currentUserId = getCurrentUserId(authentication, authorization);


        UserProfileResponse otherUser;

        try {

            otherUser = userClient.getUserByUsername(username, authorization);

        } catch (FeignException.NotFound e) {

            throw new UserNotFoundException(username);

        } catch (FeignException e) {

            throw new IllegalStateException("Не удалось получить пользователя");
        }

        Long otherUserId = otherUser.id();

        Long user1Id;
        Long user2Id;

        if (currentUserId.compareTo(otherUserId) < 0) {

            user1Id = currentUserId;
            user2Id = otherUserId;

        } else {

            user1Id = otherUserId;
            user2Id = currentUserId;
        }

        ChatRoom chatRoom = chatRoomRepository.findByUser1IdAndUser2Id(user1Id, user2Id).orElse(null);

        if (chatRoom == null) {

            return List.of();
        }

        Pageable pageable = PageRequest.of(0, 50);

        List<Message> messages = messageRepository.findLatestMessages(chatRoom.getId(), pageable);

        Collections.reverse(messages);

        return messages.stream().map(message -> toResponse(message, authorization)).toList();
    }

    @Transactional(readOnly = true)
    public List<MessageResponse> getChatMessages(Long chatId, Long beforeId, int limit, Authentication authentication, String authorization) {

        Long currentUserId = getCurrentUserId(authentication, authorization);


        ChatRoom chatRoom = chatRoomRepository.findById(chatId).orElseThrow(() -> new RuntimeException("Чат не найден"));


        boolean isParticipant = chatRoom.getUser1Id().equals(currentUserId) || chatRoom.getUser2Id().equals(currentUserId);


        if (!isParticipant) {

            throw new RuntimeException("Нет доступа к этому чату");
        }

        int safeLimit = Math.min(Math.max(limit, 1), 100);


        Pageable pageable = PageRequest.of(0, safeLimit);

        List<Message> messages;

        if (beforeId == null) {

            messages = messageRepository.findLatestMessages(chatId, pageable);

        } else {

            messages = messageRepository.findMessagesBefore(chatId, beforeId, pageable);
        }

        Collections.reverse(messages);

        return messages.stream().map(message -> toResponse(message, authorization)).toList();
    }

    @Transactional
    public void markChatAsRead(Long chatId, Authentication authentication, String authorization) {

        Long currentUserId = getCurrentUserId(authentication, authorization);

        ChatRoom chatRoom = chatRoomRepository.findById(chatId).orElseThrow(() -> new RuntimeException("Чат не найден"));

        boolean isParticipant = chatRoom.getUser1Id().equals(currentUserId) || chatRoom.getUser2Id().equals(currentUserId);

        if (!isParticipant) {
            throw new RuntimeException("Нет доступа к этому чату");
        }

        // Находим сообщения, которые реально были непрочитаны
        List<Message> unreadMessages = messageRepository.findUnreadMessages(chatId, currentUserId);

        if (unreadMessages.isEmpty()) {
            return;
        }

        // Помечаем их прочитанными
        messageRepository.markMessagesAsRead(chatId, currentUserId);

        // Отправитель — тот, кто НЕ является текущим пользователем
        for (Message message : unreadMessages) {

            MessageReadEvent event = new MessageReadEvent(chatId, message.getId(), currentUserId);

            // Отправляем событие отправителю сообщения
            messagingTemplate.convertAndSendToUser(getUsernameByUserId(message.getSenderId(), authorization), "/queue/message-read", event);
        }
    }

    private String getUsernameByUserId(
            Long userId,
            String authorization
    ) {

        try {

            UserProfileResponse user =
                    userClient.getUserById(
                            userId,
                            authorization
                    );

            return user.username();

        } catch (FeignException.NotFound e) {

            return "Удалённый пользователь";
        }
    }

    private Long getCurrentUserId(Authentication authentication, String authorization) {

        String username = authentication.getName();


        UserProfileResponse user;

        try {

            user = userClient.getUserByUsername(username, authorization);

        } catch (FeignException.NotFound e) {

            throw new UserNotFoundException(username);

        } catch (FeignException e) {

            throw new IllegalStateException("Не удалось получить пользователя");
        }


        return user.id();
    }

    private MessageResponse toResponse(
            Message message,
            String authorization
    ) {

        UserProfileResponse sender =
                getUserOrDeleted(
                        message.getSenderId(),
                        authorization
                );

        UserProfileResponse recipient =
                getUserOrDeleted(
                        message.getRecipientId(),
                        authorization
                );

        return new MessageResponse(
                message.getId(),
                message.getChatRoom().getId(),
                sender.username(),
                recipient.username(),
                message.getContent(),
                message.getCreatedAt(),
                message.isRead()
        );
    }

    private MessageResponse toResponse(Message message, String senderUsername, String recipientUsername) {

        return new MessageResponse(message.getId(), message.getChatRoom().getId(), senderUsername, recipientUsername, message.getContent(), message.getCreatedAt(), message.isRead());
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