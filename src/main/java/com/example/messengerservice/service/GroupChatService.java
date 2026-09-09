package com.example.messengerservice.service;

import com.example.messengerservice.client.UserClient;
import com.example.messengerservice.dto.UserProfileResponse;
import com.example.messengerservice.dto.chat.*;
import com.example.messengerservice.entity.Chat;
import com.example.messengerservice.entity.ChatMember;
import com.example.messengerservice.entity.ChatMemberRole;
import com.example.messengerservice.entity.ChatType;
import com.example.messengerservice.repository.ChatMemberRepository;
import com.example.messengerservice.repository.ChatRepository;
import com.example.messengerservice.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GroupChatService {

    private final ChatRepository chatRepository;
    private final ChatMemberRepository chatMemberRepository;
    private final MessageRepository messageRepository;
    private final UserClient userClient;

    private final ChatService chatService;
    private final ChatMemberService chatMemberService;
    private final ChatEventPublisher chatEventPublisher;

    @Transactional
    public ChatResponse createGroupChat(CreateGroupChatRequest request, Long currentUserId, String authorization) {

        String groupName = request.name().trim();

        if (groupName.isEmpty()) {
            throw new IllegalArgumentException("Название группы не может быть пустым");
        }

        Set<Long> uniqueUserIds = new HashSet<>(request.userIds());

        uniqueUserIds.remove(currentUserId);

        if (uniqueUserIds.isEmpty()) {
            throw new IllegalArgumentException("Добавьте хотя бы одного другого участника");
        }

        for (Long userId : uniqueUserIds) {

            if (userId == null) {
                throw new IllegalArgumentException("ID пользователя не может быть null");
            }

            getUserById(userId, authorization);
        }

        Chat chat = Chat.builder().type(ChatType.GROUP).name(groupName).createdBy(currentUserId).build();

        chat = chatRepository.save(chat);

        ChatMember owner = ChatMember.builder().chat(chat).userId(currentUserId).role(ChatMemberRole.OWNER).build();

        chatMemberRepository.save(owner);

        for (Long userId : uniqueUserIds) {

            ChatMember member = ChatMember.builder().chat(chat).userId(userId).role(ChatMemberRole.MEMBER).build();

            chatMemberRepository.save(member);
        }

        return chatService.toResponse(chat, authorization);
    }

    @Transactional
    public ChatMemberResponse addMember(Long chatId, com.example.messengerservice.dto.chat.AddChatMemberRequest request, Long currentUserId, String authorization) {

        return chatMemberService.addMember(chatId, request, currentUserId, authorization);
    }

    @Transactional
    public void removeMember(Long chatId, Long targetUserId, Long currentUserId, String authorization) {

        chatMemberService.removeMember(chatId, targetUserId, currentUserId, authorization);
    }

    @Transactional
    public void leaveGroup(Long chatId, Long currentUserId, String authorization) {

        chatMemberService.leaveGroup(chatId, currentUserId, authorization);
    }

    @Transactional
    public ChatMemberResponse changeMemberRole(Long chatId, Long targetUserId, com.example.messengerservice.dto.chat.ChangeMemberRoleRequest request, Long currentUserId, String authorization) {

        return chatMemberService.changeRole(chatId, targetUserId, request, currentUserId, authorization);
    }

    @Transactional
    public void transferOwnership(Long chatId, Long newOwnerUserId, Long currentUserId, String authorization) {

        chatMemberService.transferOwnership(chatId, newOwnerUserId, currentUserId, authorization);
    }

    @Transactional
    public ChatResponse updateGroupAvatar(Long chatId, MultipartFile file, Long currentUserId, String authorization) {

        Chat chat = chatMemberService.getChat(chatId);

        chatMemberService.checkGroup(chat);

        ChatMember currentMember = chatMemberService.getMember(chatId, currentUserId);

        checkCanManageGroup(currentMember);

        validateImage(file);

        Path uploadDirectory = Paths.get("uploads", "group-avatars");

        try {

            Files.createDirectories(uploadDirectory);

        } catch (IOException e) {

            throw new RuntimeException("Не удалось создать папку для аватаров группы", e);
        }

        deleteAvatarFile(chat.getAvatar());

        String extension = getImageExtension(file.getContentType());

        String fileName = UUID.randomUUID() + extension;

        Path filePath = uploadDirectory.resolve(fileName);

        try {

            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        } catch (IOException e) {

            throw new RuntimeException("Не удалось сохранить аватар группы", e);
        }

        String avatarUrl = "/api/chats/avatar/" + fileName;

        chat.setAvatar(avatarUrl);

        Chat savedChat = chatRepository.save(chat);


        return chatService.toResponse(savedChat, authorization);
    }

    @Transactional
    public ChatResponse deleteGroupAvatar(Long chatId, Long currentUserId, String authorization) {

        Chat chat = chatMemberService.getChat(chatId);

        chatMemberService.checkGroup(chat);

        ChatMember currentMember = chatMemberService.getMember(chatId, currentUserId);

        checkCanManageGroup(currentMember);

        if (chat.getAvatar() == null || chat.getAvatar().isBlank()) {

            throw new IllegalStateException("У группы нет аватара");
        }

        deleteAvatarFile(chat.getAvatar());

        chat.setAvatar(null);

        Chat savedChat = chatRepository.save(chat);

        return chatService.toResponse(savedChat, authorization);
    }

    private void deleteAvatarFile(String avatarUrl) {

        if (avatarUrl == null || avatarUrl.isBlank()) {

            return;
        }

        try {

            String fileName = Paths.get(avatarUrl).getFileName().toString();

            Path filePath = Paths.get("uploads", "group-avatars").resolve(fileName);

            Files.deleteIfExists(filePath);

        } catch (IOException e) {

            throw new RuntimeException("Не удалось удалить аватар группы", e);
        }
    }

    private void validateImage(MultipartFile file) {

        if (file == null || file.isEmpty()) {

            throw new IllegalArgumentException("Файл не выбран");
        }


        if (file.getSize() > 5 * 1024 * 1024) {

            throw new IllegalArgumentException("Размер файла не должен превышать 5 MB");
        }


        String contentType = file.getContentType();


        if (!"image/jpeg".equals(contentType) && !"image/png".equals(contentType) && !"image/webp".equals(contentType)) {

            throw new IllegalArgumentException("Разрешены только JPG, PNG и WEBP");
        }
    }

    private String getImageExtension(String contentType) {

        return switch (contentType) {

            case "image/jpeg" -> ".jpg";

            case "image/png" -> ".png";

            case "image/webp" -> ".webp";

            default -> throw new IllegalArgumentException("Неподдерживаемый тип изображения");
        };
    }

    private void checkCanManageGroup(ChatMember member) {

        ChatMemberRole role = member.getRole();

        if (role != ChatMemberRole.OWNER && role != ChatMemberRole.ADMIN) {

            throw new IllegalStateException("Недостаточно прав для управления группой");
        }
    }

    @Transactional
    public ChatResponse updateGroup(Long chatId, UpdateGroupChatRequest request, Long currentUserId, String authorization) {
        Chat chat = chatMemberService.getChat(chatId);

        chatMemberService.checkGroup(chat);

        ChatMember currentMember = chatMemberService.getMember(chatId, currentUserId);

        ChatMemberRole role = currentMember.getRole();

        if (role != ChatMemberRole.OWNER && role != ChatMemberRole.ADMIN) {
            throw new IllegalStateException("Недостаточно прав для изменения группы");
        }

        chat.setName(request.name());

        Chat savedChat = chatRepository.save(chat);

        return chatService.toResponse(savedChat, authorization);
    }

    @Transactional
    public void deleteGroup(Long chatId, Long currentUserId, String authorization) {

        Chat chat = chatMemberService.getChat(chatId);

        chatMemberService.checkGroup(chat);

        ChatMember currentMember = chatMemberService.getMember(chatId, currentUserId);

        if (currentMember.getRole() != ChatMemberRole.OWNER) {

            throw new IllegalStateException("Только OWNER может удалить группу");
        }

        List<ChatMember> members = chatMemberRepository.findAllByChatId(chatId);

        GroupChatEvent event = new GroupChatEvent("GROUP_DELETED", chatId, currentUserId, null, null);

        for (ChatMember member : members) {

            chatEventPublisher.publishToUser(member.getUserId(), event, authorization);
        }

        messageRepository.deleteAllByChatId(chatId);

        chatMemberRepository.deleteAllByChatId(chatId);

        deleteAvatarFile(chat.getAvatar());

        chatRepository.delete(chat);
    }

    private UserProfileResponse getUserById(Long userId, String authorization) {

        try {

            return userClient.getUserById(userId, authorization);

        } catch (feign.FeignException.NotFound e) {

            throw new IllegalArgumentException("Пользователь не найден: " + userId);

        } catch (feign.FeignException e) {

            throw new IllegalStateException("Не удалось получить пользователя");
        }
    }
}