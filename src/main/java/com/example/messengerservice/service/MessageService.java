package com.example.messengerservice.service;

import com.example.messengerservice.client.UserClient;
import com.example.messengerservice.dto.UserProfileResponse;
import com.example.messengerservice.dto.messenges.MessageResponse;
import com.example.messengerservice.dto.messenges.MessageSentEvent;
import com.example.messengerservice.dto.messenges.SendMessageRequest;
import com.example.messengerservice.dto.websocket.MessageReadEvent;
import com.example.messengerservice.entity.Chat;
import com.example.messengerservice.entity.ChatMember;
import com.example.messengerservice.entity.Message;
import com.example.messengerservice.exception.UserNotFoundException;
import com.example.messengerservice.repository.ChatMemberRepository;
import com.example.messengerservice.repository.ChatRepository;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MessageService {


    private final MessageRepository messageRepository;

    private final ChatRepository chatRepository;

    private final ChatMemberRepository chatMemberRepository;

    private final UserClient userClient;

    private final SimpMessagingTemplate messagingTemplate;

    private final NotificationEventPublisher notificationEventPublisher;


    // =====================================================
    // SEND MESSAGE
    // =====================================================

    @Transactional
    public MessageResponse sendMessage(
            SendMessageRequest request,
            Authentication authentication,
            String authorization
    ) {

        Long senderId =
                getCurrentUserId(
                        authentication,
                        authorization
                );


        // =================================================
        // GET CHAT
        // =================================================

        Chat chat =
                chatRepository.findById(
                        request.chatId()
                ).orElseThrow(() ->
                        new RuntimeException(
                                "Чат не найден"
                        )
                );


        // =================================================
        // CHECK MEMBERSHIP
        // =================================================

        boolean isMember =
                chatMemberRepository.existsByChatIdAndUserId(
                        chat.getId(),
                        senderId
                );

        if (!isMember) {

            throw new RuntimeException(
                    "Нет доступа к этому чату"
            );
        }


        // =================================================
        // VALIDATE CONTENT
        // =================================================

        String content =
                request.content().trim();

        if (content.isEmpty()) {

            throw new IllegalArgumentException(
                    "Сообщение не может быть пустым"
            );
        }


        // =================================================
        // CREATE MESSAGE
        // =================================================

        Message message =
                Message.builder()
                        .chat(chat)
                        .senderId(senderId)
                        .content(content)
                        .build();


        Message savedMessage =
                messageRepository.save(message);


        // =================================================
        // UPDATE CHAT LAST MESSAGE
        // =================================================

        chat.setLastMessageAt(
                savedMessage.getCreatedAt()
        );

        chatRepository.save(chat);


        // =================================================
        // GET SENDER
        // =================================================

        UserProfileResponse sender =
                getUserOrDeleted(
                        senderId,
                        authorization
                );


        // =================================================
        // PUBLISH NOTIFICATIONS
        // =================================================

        publishMessageNotifications(
                savedMessage,
                sender,
                authorization
        );


        // =================================================
        // RESPONSE
        // =================================================

        return new MessageResponse(
                savedMessage.getId(),
                savedMessage.getChat().getId(),
                savedMessage.getSenderId(),
                sender.username(),
                sender.avatar(),
                savedMessage.getContent(),
                savedMessage.getCreatedAt(),
                false
        );
    }


    // =====================================================
    // PUBLISH MESSAGE NOTIFICATIONS
    // =====================================================

    private void publishMessageNotifications(
            Message message,
            UserProfileResponse sender,
            String authorization
    ) {

        List<ChatMember> members =
                chatMemberRepository.findAllByChatId(
                        message.getChat().getId()
                );

        if (members.isEmpty()) {
            return;
        }


        // =================================================
        // COLLECT RECIPIENT IDS
        // =================================================

        List<Long> recipientIds =
                members.stream()
                        .map(ChatMember::getUserId)
                        .filter(userId ->
                                !userId.equals(
                                        message.getSenderId()
                                )
                        )
                        .distinct()
                        .toList();


        if (recipientIds.isEmpty()) {
            return;
        }


        // =================================================
        // BATCH LOAD USERS
        // =================================================

        List<UserProfileResponse> recipients;

        try {

            recipients =
                    userClient.getUsersByIds(
                            recipientIds,
                            authorization
                    );

        } catch (FeignException e) {

            System.out.println(
                    "❌ Ошибка AuthService при batch-запросе "
                            + "получателей: "
                            + e.status()
            );

            return;
        }


        // =================================================
        // USER ID -> USER
        // =================================================

        Map<Long, UserProfileResponse> usersById =
                new HashMap<>();

        for (UserProfileResponse user : recipients) {

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
        // PUBLISH EVENTS
        // =================================================

        for (Long recipientId : recipientIds) {

            UserProfileResponse recipient =
                    usersById.get(recipientId);

            if (recipient == null ||
                    recipient.username() == null ||
                    recipient.username().isBlank()) {

                continue;
            }


            MessageSentEvent event =
                    new MessageSentEvent(
                            "MESSAGE_SENT",
                            message.getId(),
                            message.getChat().getId(),
                            sender.username(),
                            recipient.username(),
                            message.getContent()
                    );


            notificationEventPublisher
                    .publishMessageSent(event);
        }
    }


    // =====================================================
    // GET CHAT MESSAGES
    // =====================================================

    @Transactional(readOnly = true)
    public List<MessageResponse> getChatMessages(
            Long chatId,
            Long beforeId,
            int limit,
            Authentication authentication,
            String authorization
    ) {

        Long currentUserId =
                getCurrentUserId(
                        authentication,
                        authorization
                );


        // =================================================
        // GET CHAT
        // =================================================

        Chat chat =
                chatRepository.findById(chatId)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Чат не найден"
                                )
                        );


        // =================================================
        // CHECK MEMBERSHIP
        // =================================================

        boolean isMember =
                chatMemberRepository
                        .existsByChatIdAndUserId(
                                chatId,
                                currentUserId
                        );

        if (!isMember) {

            throw new RuntimeException(
                    "Нет доступа к этому чату"
            );
        }


        // =================================================
        // LIMIT
        // =================================================

        int safeLimit =
                Math.min(
                        Math.max(limit, 1),
                        100
                );


        Pageable pageable =
                PageRequest.of(
                        0,
                        safeLimit
                );


        // =================================================
        // LOAD MESSAGES
        // =================================================

        List<Message> messages;

        if (beforeId == null) {

            messages =
                    messageRepository.findLatestMessages(
                            chatId,
                            pageable
                    );

        } else {

            messages =
                    messageRepository.findMessagesBefore(
                            chatId,
                            beforeId,
                            pageable
                    );
        }


        if (messages.isEmpty()) {

            return List.of();
        }


        // =================================================
        // RESTORE ASCENDING ORDER
        // =================================================

        Collections.reverse(messages);


        // =================================================
        // COLLECT SENDER IDS
        // =================================================

        List<Long> senderIds =
                messages.stream()
                        .map(Message::getSenderId)
                        .distinct()
                        .toList();


        // =================================================
        // BATCH LOAD SENDERS
        // =================================================

        List<UserProfileResponse> users;

        try {

            users =
                    userClient.getUsersByIds(
                            senderIds,
                            authorization
                    );

        } catch (FeignException e) {

            throw new IllegalStateException(
                    "Не удалось получить пользователей"
            );
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
        // LOAD CHAT MEMBERS
        // =================================================

        List<ChatMember> chatMembers =
                chatMemberRepository.findAllByChatId(
                        chatId
                );


        // =================================================
        // BUILD RESPONSE
        // =================================================

        return messages.stream()
                .map(message -> {

                    UserProfileResponse sender =
                            usersById.get(
                                    message.getSenderId()
                            );


                    if (sender == null) {

                        sender =
                                new UserProfileResponse(
                                        message.getSenderId(),
                                        "Удалённый пользователь",
                                        null
                                );
                    }


                    // =========================================
                    // READ STATUS
                    // =========================================

                    boolean read =
                            isMessageRead(
                                    message,
                                    currentUserId,
                                    chatMembers
                            );


                    return new MessageResponse(
                            message.getId(),
                            message.getChat().getId(),
                            message.getSenderId(),
                            sender.username(),
                            sender.avatar(),
                            message.getContent(),
                            message.getCreatedAt(),
                            read
                    );

                })
                .toList();
    }


    // =====================================================
    // CHECK MESSAGE READ STATUS
    // =====================================================

    private boolean isMessageRead(
            Message message,
            Long currentUserId,
            List<ChatMember> chatMembers
    ) {

        // =============================================
        // MESSAGE FROM OTHER USER
        // =============================================

        if (!message.getSenderId().equals(currentUserId)) {

            return true;
        }


        // =============================================
        // OWN MESSAGE
        // CHECK OTHER MEMBERS
        // =============================================

        return chatMembers.stream()

                .filter(member ->
                        !member.getUserId()
                                .equals(currentUserId)
                )

                .anyMatch(member -> {

                    Long lastReadMessageId =
                            member.getLastReadMessageId();


                    return lastReadMessageId != null
                            &&
                            lastReadMessageId >= message.getId();

                });
    }


    // =====================================================
    // MARK CHAT AS READ
    // =====================================================

    @Transactional
    public void markChatAsRead(
            Long chatId,
            Authentication authentication,
            String authorization
    ) {

        Long currentUserId =
                getCurrentUserId(
                        authentication,
                        authorization
                );


        ChatMember member =
                chatMemberRepository
                        .findByChatIdAndUserId(
                                chatId,
                                currentUserId
                        )
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Нет доступа к этому чату"
                                )
                        );


        Message lastMessage =
                messageRepository
                        .findTopByChatIdOrderByIdDesc(
                                chatId
                        )
                        .orElse(null);


        if (lastMessage == null) {
            return;
        }


        Long currentLastRead =
                member.getLastReadMessageId();


        if (currentLastRead != null &&
                currentLastRead >= lastMessage.getId()) {

            return;
        }


        // =================================================
        // SAVE LAST READ MESSAGE
        // =================================================

        member.setLastReadMessageId(
                lastMessage.getId()
        );

        chatMemberRepository.save(member);


        // =================================================
        // READ EVENT
        // =================================================

        List<ChatMember> members =
                chatMemberRepository.findAllByChatId(
                        chatId
                );


        MessageReadEvent event =
                new MessageReadEvent(
                        chatId,
                        lastMessage.getId(),
                        currentUserId
                );


        // =================================================
        // COLLECT OTHER USERS
        // =================================================

        List<Long> userIds =
                members.stream()
                        .map(ChatMember::getUserId)
                        .filter(userId ->
                                !userId.equals(
                                        currentUserId
                                )
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
                    "❌ Ошибка AuthService при batch-запросе "
                            + "read event: "
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
        // SEND READ EVENT
        // =================================================

        for (Long userId : userIds) {

            UserProfileResponse user =
                    usersById.get(userId);

            if (user == null ||
                    user.username() == null ||
                    user.username().isBlank()) {

                continue;
            }


            messagingTemplate.convertAndSendToUser(
                    user.username(),
                    "/queue/message-read",
                    event
            );
        }
    }


    // =====================================================
    // UNREAD COUNT
    // =====================================================

    @Transactional(readOnly = true)
    public long getUnreadCount(
            Long chatId,
            Long currentUserId
    ) {

        ChatMember member =
                chatMemberRepository
                        .findByChatIdAndUserId(
                                chatId,
                                currentUserId
                        )
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Нет доступа к этому чату"
                                )
                        );


        Long lastReadMessageId =
                member.getLastReadMessageId();


        if (lastReadMessageId == null) {

            return messageRepository
                    .countUnreadMessagesFromBeginning(
                            chatId,
                            currentUserId
                    );
        }


        return messageRepository
                .countUnreadMessages(
                        chatId,
                        lastReadMessageId,
                        currentUserId
                );
    }


    // =====================================================
    // CURRENT USER
    // =====================================================

    private Long getCurrentUserId(
            Authentication authentication,
            String authorization
    ) {

        if (authentication == null ||
                authentication.getName() == null) {

            throw new RuntimeException(
                    "Пользователь не авторизован"
            );
        }


        String username =
                authentication.getName();


        try {

            UserProfileResponse user =
                    userClient.getUserByUsername(
                            username,
                            authorization
                    );

            return user.id();

        } catch (FeignException.NotFound e) {

            throw new UserNotFoundException(
                    username
            );

        } catch (FeignException e) {

            throw new IllegalStateException(
                    "Не удалось получить пользователя"
            );
        }
    }


    // =====================================================
    // USER
    // =====================================================

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

        } catch (FeignException e) {

            throw new IllegalStateException(
                    "Не удалось получить пользователя"
            );
        }
    }
}