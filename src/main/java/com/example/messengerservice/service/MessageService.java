package com.example.messengerservice.service;

import com.example.messengerservice.client.UserClient;
import com.example.messengerservice.dto.UserProfileResponse;
import com.example.messengerservice.dto.messenges.MessageResponse;
import com.example.messengerservice.dto.messenges.MessageSentEvent;
import com.example.messengerservice.dto.messenges.SendMessageRequest;
import com.example.messengerservice.dto.websocket.MessageDeletedEvent;
import com.example.messengerservice.dto.websocket.MessageReadEvent;
import com.example.messengerservice.entity.*;
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

import java.util.*;

@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageRepository messageRepository;
    private final ChatRepository chatRepository;
    private final ChatMemberRepository chatMemberRepository;
    private final UserClient userClient;
    private final SimpMessagingTemplate messagingTemplate;
    private final NotificationEventPublisher notificationEventPublisher;
    private final MessageManagementService messageManagementService;

    @Transactional
    public MessageResponse sendMessage(SendMessageRequest request, Authentication authentication, String authorization) {
        Long senderId = getCurrentUserId(authentication, authorization);
        Chat chat = chatRepository.findById(request.chatId()).orElseThrow(() -> new RuntimeException("Чат не найден"));
        boolean isMember = chatMemberRepository.existsByChatIdAndUserId(chat.getId(), senderId);
        if (!isMember) {
            throw new RuntimeException("Нет доступа к этому чату");
        }
        String content = request.content().trim();
        if (content.isEmpty()) {
            throw new IllegalArgumentException("Сообщение не может быть пустым");
        }
        Message message = Message.builder().chat(chat).senderId(senderId).content(content).build();
        Message savedMessage = messageRepository.save(message);
        chat.setLastMessageAt(savedMessage.getCreatedAt());
        chatRepository.save(chat);
        UserProfileResponse sender = getUserOrDeleted(senderId, authorization);
        publishMessageNotifications(savedMessage, sender, authorization);
        return new MessageResponse(savedMessage.getId(), savedMessage.getChat().getId(), savedMessage.getSenderId(), sender.username(), sender.avatar(), savedMessage.getContent(), savedMessage.getCreatedAt(), false);
    }

    @Transactional
    public void deleteMessage(Long id, Authentication authentication, String authorization) {
        Long userId = getCurrentUserId(authentication, authorization);
        Message message = messageRepository.findById(id).orElseThrow(()-> new RuntimeException("Message not found"));
        Chat chat = message.getChat();
        ChatType chatType = chat.getType();
        if (userId.equals(message.getSenderId())){
            messageManagementService.deleteMessage(id);

        }else {
            if (chatType == ChatType.PRIVATE) {
                throw new RuntimeException("ноу ноу ноу мистр фишь, не твое сообщение");
            }
            if (chatType == ChatType.GROUP) {
                ChatMember chatMember = chatMemberRepository.findByChatIdAndUserId(chat.getId(), userId)
                        .orElseThrow(() -> new RuntimeException("Что то не найдено"));
                ChatMemberRole role = chatMember.getRole();
                if (role == ChatMemberRole.OWNER || role == ChatMemberRole.ADMIN) {
                    messageManagementService.deleteMessage(id);
                } else {
                    throw new RuntimeException("Недостаточо прав");
                }
            }
        }
        List<ChatMember> members = chatMemberRepository.findAllByChatId(chat.getId());
        MessageDeletedEvent messageDeletedEvent = new MessageDeletedEvent(message.getId(), chat.getId());
        List<Long> userIds = members.stream().map(ChatMember::getUserId).filter(userCurrentId ->
                !userCurrentId.equals(userId)).distinct().toList();
        if (userIds.isEmpty()) {
            return;
        }
        List<UserProfileResponse> users;
        try {
            users = userClient.getUsersByIds(userIds, authorization);
        } catch (FeignException e) {
            System.out.println("❌ Ошибка AuthService при batch-запросе " + "delete event: " + e.status());
            return;
        }
        Map<Long, UserProfileResponse> usersById = new HashMap<>();
        for (UserProfileResponse user : users) {
            if (user == null || user.id() == null) {
                continue;
            }
            usersById.put(user.id(), user);
        }
        for (Long userCurrentId : userIds) {
            UserProfileResponse user = usersById.get(userCurrentId);
            if (user == null || user.username() == null || user.username().isBlank()) {
                continue;
            }
            messagingTemplate.convertAndSendToUser(user.username(), "/queue/message-deleted", messageDeletedEvent);
        }
    }

    private void publishMessageNotifications(Message message, UserProfileResponse sender, String authorization) {
        List<ChatMember> members = chatMemberRepository.findAllByChatId(message.getChat().getId());
        if (members.isEmpty()) {
            return;
        }
        List<Long> recipientIds = members.stream().map(ChatMember::getUserId).filter(userId ->
                !userId.equals(message.getSenderId())).distinct().toList();
        if (recipientIds.isEmpty()) {
            return;
        }
        List<UserProfileResponse> recipients;
        try {
            recipients = userClient.getUsersByIds(recipientIds, authorization);
        } catch (FeignException e) {
            System.out.println("❌ Ошибка AuthService при batch-запросе " + "получателей: " + e.status());
            return;
        }
        Map<Long, UserProfileResponse> usersById = new HashMap<>();
        for (UserProfileResponse user : recipients) {
            if (user == null || user.id() == null) {
                continue;
            }
            usersById.put(user.id(), user);
        }
        for (Long recipientId : recipientIds) {
            UserProfileResponse recipient = usersById.get(recipientId);
            if (recipient == null || recipient.username() == null || recipient.username().isBlank()) {
                continue;
            }
            MessageSentEvent event = new MessageSentEvent("MESSAGE_SENT", message.getId(), message.getChat().getId(), message.getChat().getName(), sender.username(), recipient.username(), message.getContent());
            notificationEventPublisher.publishMessageSent(event);
        }
    }

    @Transactional(readOnly = true)
    public List<MessageResponse> getChatMessages(Long chatId, Long beforeId, int limit, Authentication authentication, String authorization) {
        Long currentUserId = getCurrentUserId(authentication, authorization);
        Chat chat = chatRepository.findById(chatId).orElseThrow(() -> new RuntimeException("Чат не найден"));
        boolean isMember = chatMemberRepository.existsByChatIdAndUserId(chatId, currentUserId);
        if (!isMember) {
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
        if (messages.isEmpty()) {
            return List.of();
        }
        Collections.reverse(messages);
        List<Long> senderIds = messages.stream().map(Message::getSenderId).distinct().toList();
        List<UserProfileResponse> users;
        try {
            users = userClient.getUsersByIds(senderIds, authorization);
        } catch (FeignException e) {
            throw new IllegalStateException("Не удалось получить пользователей");
        }
        Map<Long, UserProfileResponse> usersById = new HashMap<>();
        for (UserProfileResponse user : users) {
            if (user == null || user.id() == null) {
                continue;
            }
            usersById.put(user.id(), user);
        }
        List<ChatMember> chatMembers = chatMemberRepository.findAllByChatId(chatId);
        return messages.stream().map(message -> {
            UserProfileResponse sender = usersById.get(message.getSenderId());
            if (sender == null) {
                sender = new UserProfileResponse(message.getSenderId(), "Удалённый пользователь", null);
            }
            boolean read = isMessageRead(message, currentUserId, chatMembers);
            return new MessageResponse(message.getId(), message.getChat().getId(), message.getSenderId(), sender.username(), sender.avatar(), message.getContent(), message.getCreatedAt(), read);

        }).toList();
    }

    private boolean isMessageRead(Message message, Long currentUserId, List<ChatMember> chatMembers) {
        if (!message.getSenderId().equals(currentUserId)) {
            return true;
        }
        return chatMembers.stream().filter(member -> !member.getUserId().equals(currentUserId)).anyMatch(member -> {
            Long lastReadMessageId = member.getLastReadMessageId();
            return lastReadMessageId != null && lastReadMessageId >= message.getId();
        });
    }

    @Transactional
    public void markChatAsRead(Long chatId, Authentication authentication, String authorization) {
        Long currentUserId = getCurrentUserId(authentication, authorization);
        ChatMember member = chatMemberRepository.findByChatIdAndUserId(chatId, currentUserId).orElseThrow(()
                -> new RuntimeException("Нет доступа к этому чату"));
        Message lastMessage = messageRepository.findTopByChatIdOrderByIdDesc(chatId).orElse(null);
        if (lastMessage == null) {
            return;
        }
        Long currentLastRead = member.getLastReadMessageId();
        if (currentLastRead != null && currentLastRead >= lastMessage.getId()) {
            return;
        }
        member.setLastReadMessageId(lastMessage.getId());
        chatMemberRepository.save(member);
        List<ChatMember> members = chatMemberRepository.findAllByChatId(chatId);
        MessageReadEvent event = new MessageReadEvent(chatId, lastMessage.getId(), currentUserId);
        List<Long> userIds = members.stream().map(ChatMember::getUserId).filter(userId -> !userId.equals(currentUserId)).distinct().toList();
        if (userIds.isEmpty()) {
            return;
        }
        List<UserProfileResponse> users;
        try {
            users = userClient.getUsersByIds(userIds, authorization);
        } catch (FeignException e) {
            System.out.println("❌ Ошибка AuthService при batch-запросе " + "read event: " + e.status());
            return;
        }
        Map<Long, UserProfileResponse> usersById = new HashMap<>();
        for (UserProfileResponse user : users) {
            if (user == null || user.id() == null) {
                continue;
            }
            usersById.put(user.id(), user);
        }
        for (Long userId : userIds) {
            UserProfileResponse user = usersById.get(userId);
            if (user == null || user.username() == null || user.username().isBlank()) {
                continue;
            }
            messagingTemplate.convertAndSendToUser(user.username(), "/queue/message-read", event);
        }
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(Long chatId, Long currentUserId) {
        ChatMember member = chatMemberRepository.findByChatIdAndUserId(chatId, currentUserId).orElseThrow(() -> new RuntimeException("Нет доступа к этому чату"));
        Long lastReadMessageId = member.getLastReadMessageId();
        if (lastReadMessageId == null) {
            return messageRepository.countUnreadMessagesFromBeginning(chatId, currentUserId);
        }
        return messageRepository.countUnreadMessages(chatId, lastReadMessageId, currentUserId);
    }

    private Long getCurrentUserId(Authentication authentication, String authorization) {
        if (authentication == null || authentication.getName() == null) {
            throw new RuntimeException("Пользователь не авторизован");
        }
        String username = authentication.getName();
        try {
            UserProfileResponse user = userClient.getUserByUsername(username, authorization);
            return user.id();
        } catch (FeignException.NotFound e) {
            throw new UserNotFoundException(username);
        } catch (FeignException e) {
            throw new IllegalStateException("Не удалось получить пользователя");
        }
    }

    private UserProfileResponse getUserOrDeleted(Long userId, String authorization) {
        try {
            return userClient.getUserById(userId, authorization);
        } catch (FeignException.NotFound e) {
            return new UserProfileResponse(userId, "Удалённый пользователь", null);
        } catch (FeignException e) {
            throw new IllegalStateException("Не удалось получить пользователя");
        }
    }
}