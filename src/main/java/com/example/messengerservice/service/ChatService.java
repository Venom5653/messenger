package com.example.messengerservice.service;

import com.example.messengerservice.client.UserClient;
import com.example.messengerservice.dto.UserProfileResponse;
import com.example.messengerservice.dto.chat.ChatMemberResponse;
import com.example.messengerservice.dto.chat.ChatResponse;
import com.example.messengerservice.dto.chat.CreatePrivateChatRequest;
import com.example.messengerservice.entity.Chat;
import com.example.messengerservice.entity.ChatMember;
import com.example.messengerservice.entity.ChatMemberRole;
import com.example.messengerservice.entity.ChatType;
import com.example.messengerservice.entity.Message;
import com.example.messengerservice.exception.UserNotFoundException;
import com.example.messengerservice.repository.ChatMemberRepository;
import com.example.messengerservice.repository.ChatRepository;
import com.example.messengerservice.repository.MessageRepository;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatRepository chatRepository;
    private final ChatMemberRepository chatMemberRepository;
    private final UserClient userClient;
    private final MessageRepository messageRepository;

    @Transactional
    public ChatResponse createPrivateChat(CreatePrivateChatRequest request, Long currentUserId, String authorization) {

        String username = request.username().trim();

        if (username.isEmpty()) {
            throw new IllegalArgumentException("Username не может быть пустым");
        }

        UserProfileResponse otherUser = getUserByUsername(username, authorization);

        Long otherUserId = otherUser.id();

        if (currentUserId.equals(otherUserId)) {
            throw new IllegalArgumentException("Нельзя создать чат с самим собой");
        }

        Chat existingChat = chatMemberRepository.findPrivateChatBetweenUsers(currentUserId, otherUserId, ChatType.PRIVATE).orElse(null);

        if (existingChat != null) {
            return toResponse(existingChat, authorization, currentUserId);
        }

        Chat chat = Chat.builder().type(ChatType.PRIVATE).createdBy(currentUserId).build();

        chat = chatRepository.save(chat);

        ChatMember firstMember = ChatMember.builder().chat(chat).userId(currentUserId).role(ChatMemberRole.OWNER).build();

        ChatMember secondMember = ChatMember.builder().chat(chat).userId(otherUserId).role(ChatMemberRole.MEMBER).build();

        chatMemberRepository.save(firstMember);
        chatMemberRepository.save(secondMember);

        return toResponse(chat, authorization, currentUserId);
    }

    @Transactional(readOnly = true)
    public List<ChatResponse> getMyChats(Long currentUserId, String authorization) {

        List<ChatMember> memberships = chatMemberRepository.findAllChatsByUserId(currentUserId);

        if (memberships.isEmpty()) {
            return List.of();
        }

        List<Long> chatIds = memberships.stream().map(ChatMember::getChat).map(Chat::getId).distinct().toList();

        List<Message> lastMessages = messageRepository.findLastMessagesByChatIds(chatIds);

        Map<Long, Message> lastMessageByChatId = lastMessages.stream().collect(Collectors.toMap(message -> message.getChat().getId(), message -> message));

        List<Object[]> unreadResults = chatMemberRepository.countUnreadMessagesByUser(currentUserId);

        Map<Long, Long> unreadCountByChatId = new HashMap<>();

        for (Object[] result : unreadResults) {

            Long chatId = ((Number) result[0]).longValue();
            Long unreadCount = ((Number) result[1]).longValue();

            unreadCountByChatId.put(chatId, unreadCount);
        }

        List<ChatMember> allMembers = chatMemberRepository.findAllByChatIdIn(chatIds);

        Map<Long, List<ChatMember>> membersByChat = allMembers.stream().collect(Collectors.groupingBy(member -> member.getChat().getId()));

        List<Long> userIds = allMembers.stream().map(ChatMember::getUserId).distinct().toList();

        List<UserProfileResponse> users;

        try {

            users = userClient.getUsersByIds(userIds, authorization);

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

        List<ChatResponse> responses = new ArrayList<>();

        for (ChatMember membership : memberships) {

            Chat chat = membership.getChat();

            Message lastMessage = lastMessageByChatId.get(chat.getId());

            String lastMessageContent = lastMessage != null ? lastMessage.getContent() : null;

            List<ChatMember> chatMembers = membersByChat.getOrDefault(chat.getId(), List.of());

            List<ChatMemberResponse> memberResponses = new ArrayList<>();

            for (ChatMember member : chatMembers) {

                UserProfileResponse user = usersById.get(member.getUserId());

                if (user == null) {

                    user = new UserProfileResponse(member.getUserId(), "Удалённый пользователь", null);
                }

                memberResponses.add(new ChatMemberResponse(member.getUserId(), user.username(), user.avatar(), member.getRole(), member.getJoinedAt(), member.getLastReadMessageId()));
            }

            long unreadCount = unreadCountByChatId.getOrDefault(chat.getId(), 0L);

            responses.add(new ChatResponse(chat.getId(), chat.getType(), chat.getName(), chat.getAvatar(), chat.getCreatedBy(), chat.getLastMessageAt(), chat.getCreatedAt(), lastMessageContent, unreadCount, memberResponses));
        }

        return responses;
    }

    @Transactional(readOnly = true)
    public ChatResponse getChat(Long chatId, Long currentUserId, String authorization) {

        Chat chat = chatRepository.findById(chatId).orElseThrow(() -> new RuntimeException("Чат не найден"));

        boolean isMember = chatMemberRepository.existsByChatIdAndUserId(chatId, currentUserId);

        if (!isMember) {
            throw new RuntimeException("Нет доступа к этому чату");
        }

        return toResponse(chat, authorization, currentUserId);
    }

    public ChatResponse toResponse(Chat chat, String authorization) {

        return toResponse(chat, authorization, null);
    }

    public ChatResponse toResponse(Chat chat, String authorization, Long currentUserId) {

        Message lastMessage = messageRepository.findTopByChatIdOrderByIdDesc(chat.getId()).orElse(null);

        String lastMessageContent = lastMessage != null ? lastMessage.getContent() : null;

        List<ChatMember> members = chatMemberRepository.findAllByChatId(chat.getId());

        long unreadCount = 0L;

        if (currentUserId != null) {

            List<Object[]> unreadResults = chatMemberRepository.countUnreadMessagesByUser(currentUserId);

            for (Object[] result : unreadResults) {

                Long resultChatId = ((Number) result[0]).longValue();

                if (resultChatId.equals(chat.getId())) {

                    unreadCount = ((Number) result[1]).longValue();

                    break;
                }
            }
        }

        if (members.isEmpty()) {

            return new ChatResponse(chat.getId(), chat.getType(), chat.getName(), chat.getAvatar(), chat.getCreatedBy(), chat.getLastMessageAt(), chat.getCreatedAt(), lastMessageContent, unreadCount, List.of());
        }

        List<Long> userIds = members.stream().map(ChatMember::getUserId).distinct().toList();

        List<UserProfileResponse> users;

        try {

            users = userClient.getUsersByIds(userIds, authorization);

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

        List<ChatMemberResponse> responses = new ArrayList<>();

        for (ChatMember member : members) {

            UserProfileResponse user = usersById.get(member.getUserId());

            if (user == null) {

                user = new UserProfileResponse(member.getUserId(), "Удалённый пользователь", null);
            }

            responses.add(new ChatMemberResponse(member.getUserId(), user.username(), user.avatar(), member.getRole(), member.getJoinedAt(), member.getLastReadMessageId()));
        }

        return new ChatResponse(chat.getId(), chat.getType(), chat.getName(), chat.getAvatar(), chat.getCreatedBy(), chat.getLastMessageAt(), chat.getCreatedAt(), lastMessageContent, unreadCount, responses);
    }

    private UserProfileResponse getUserByUsername(String username, String authorization) {

        try {

            return userClient.getUserByUsername(username, authorization);

        } catch (FeignException.NotFound e) {

            throw new UserNotFoundException(username);

        } catch (FeignException e) {

            throw new IllegalStateException("Не удалось получить пользователя");
        }
    }
}