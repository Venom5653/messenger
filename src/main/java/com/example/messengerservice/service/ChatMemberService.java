package com.example.messengerservice.service;

import com.example.messengerservice.client.UserClient;
import com.example.messengerservice.dto.UserProfileResponse;
import com.example.messengerservice.dto.chat.AddChatMemberRequest;
import com.example.messengerservice.dto.chat.ChangeMemberRoleRequest;
import com.example.messengerservice.dto.chat.ChatMemberResponse;
import com.example.messengerservice.dto.chat.GroupChatEvent;
import com.example.messengerservice.entity.Chat;
import com.example.messengerservice.entity.ChatMember;
import com.example.messengerservice.entity.ChatMemberRole;
import com.example.messengerservice.entity.ChatType;
import com.example.messengerservice.repository.ChatMemberRepository;
import com.example.messengerservice.repository.ChatRepository;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatMemberService {

    private final ChatRepository chatRepository;
    private final ChatMemberRepository chatMemberRepository;
    private final UserClient userClient;
    private final ChatEventPublisher chatEventPublisher;


    // =====================================================
    // ADD MEMBER
    // =====================================================

    @Transactional
    public ChatMemberResponse addMember(
            Long chatId,
            AddChatMemberRequest request,
            Long currentUserId,
            String authorization
    ) {

        Chat chat = getChat(chatId);

        checkGroup(chat);

        ChatMember currentMember =
                getMember(
                        chatId,
                        currentUserId
                );

        checkCanAddMember(currentMember);

        Long newUserId = request.userId();

        if (currentUserId.equals(newUserId)) {
            throw new IllegalArgumentException(
                    "Вы уже являетесь участником группы"
            );
        }

        if (chatMemberRepository.existsByChatIdAndUserId(
                chatId,
                newUserId
        )) {
            throw new IllegalArgumentException(
                    "Пользователь уже состоит в группе"
            );
        }

        UserProfileResponse user =
                getUserById(
                        newUserId,
                        authorization
                );

        ChatMember member =
                ChatMember.builder()
                        .chat(chat)
                        .userId(newUserId)
                        .role(ChatMemberRole.MEMBER)
                        .build();

        ChatMember savedMember =
                chatMemberRepository.save(member);


        // =================================================
        // GROUP EVENT: MEMBER_ADDED
        // =================================================

        chatEventPublisher.publishToChatMembers(
                chatId,
                new GroupChatEvent(
                        "MEMBER_ADDED",
                        chatId,
                        currentUserId,
                        newUserId,
                        ChatMemberRole.MEMBER
                ),
                authorization
        );

        return toResponse(
                savedMember,
                user
        );
    }


    // =====================================================
    // REMOVE MEMBER
    // =====================================================

    @Transactional
    public void removeMember(
            Long chatId,
            Long targetUserId,
            Long currentUserId,
            String authorization
    ) {

        Chat chat = getChat(chatId);

        checkGroup(chat);

        ChatMember currentMember =
                getMember(
                        chatId,
                        currentUserId
                );

        ChatMember targetMember =
                getMember(
                        chatId,
                        targetUserId
                );

        if (currentUserId.equals(targetUserId)) {
            throw new IllegalArgumentException(
                    "Нельзя удалить самого себя. " +
                            "Используйте выход из группы."
            );
        }

        checkCanRemoveMember(
                currentMember,
                targetMember
        );


        // =================================================
        // EVENT
        // =================================================

        GroupChatEvent event =
                new GroupChatEvent(
                        "MEMBER_REMOVED",
                        chatId,
                        currentUserId,
                        targetUserId,
                        null
                );


        // =================================================
        // УВЕДОМЛЯЕМ УДАЛЯЕМОГО
        // =================================================

        chatEventPublisher.publishToUser(
                targetUserId,
                event,
                authorization
        );


        // =================================================
        // УВЕДОМЛЯЕМ ОСТАЛЬНЫХ
        // =================================================

        chatEventPublisher.publishToChatMembers(
                chatId,
                event,
                authorization,
                targetUserId
        );

        chatMemberRepository.delete(
                targetMember
        );
    }


    // =====================================================
    // LEAVE GROUP
    // =====================================================

    @Transactional
    public void leaveGroup(
            Long chatId,
            Long currentUserId,
            String authorization
    ) {

        Chat chat = getChat(chatId);

        checkGroup(chat);

        ChatMember currentMember =
                getMember(
                        chatId,
                        currentUserId
                );

        if (currentMember.getRole()
                == ChatMemberRole.OWNER) {

            throw new IllegalStateException(
                    "Владелец не может покинуть группу. " +
                            "Сначала передайте права OWNER " +
                            "другому участнику."
            );
        }

        GroupChatEvent event =
                new GroupChatEvent(
                        "MEMBER_LEFT",
                        chatId,
                        currentUserId,
                        currentUserId,
                        null
                );

        chatEventPublisher.publishToChatMembers(
                chatId,
                event,
                authorization,
                currentUserId
        );

        chatMemberRepository.delete(
                currentMember
        );
    }


    // =====================================================
    // CHANGE ROLE
    // =====================================================

    @Transactional
    public ChatMemberResponse changeRole(
            Long chatId,
            Long targetUserId,
            ChangeMemberRoleRequest request,
            Long currentUserId,
            String authorization
    ) {

        Chat chat = getChat(chatId);

        checkGroup(chat);

        ChatMember currentMember =
                getMember(
                        chatId,
                        currentUserId
                );

        ChatMember targetMember =
                getMember(
                        chatId,
                        targetUserId
                );

        ChatMemberRole currentRole =
                currentMember.getRole();

        ChatMemberRole targetRole =
                targetMember.getRole();

        ChatMemberRole newRole =
                request.role();


        // =================================================
        // ONLY OWNER
        // =================================================

        if (currentRole != ChatMemberRole.OWNER) {
            throw new IllegalStateException(
                    "Только OWNER может " +
                            "изменять роли участников"
            );
        }


        // =================================================
        // OWNER ROLE
        // =================================================

        if (targetRole == ChatMemberRole.OWNER) {
            throw new IllegalStateException(
                    "Роль OWNER изменяется " +
                            "только через передачу владения"
            );
        }


        // =================================================
        // CANNOT SET OWNER HERE
        // =================================================

        if (newRole == ChatMemberRole.OWNER) {
            throw new IllegalArgumentException(
                    "Для передачи OWNER " +
                            "используйте отдельную операцию"
            );
        }


        // =================================================
        // CANNOT CHANGE OWN ROLE
        // =================================================

        if (currentUserId.equals(targetUserId)) {
            throw new IllegalArgumentException(
                    "OWNER не может изменить " +
                            "собственную роль"
            );
        }

        targetMember.setRole(newRole);

        ChatMember savedMember =
                chatMemberRepository.save(
                        targetMember
                );

        UserProfileResponse user =
                getUserById(
                        targetUserId,
                        authorization
                );

        chatEventPublisher.publishToChatMembers(
                chatId,
                new GroupChatEvent(
                        "ROLE_CHANGED",
                        chatId,
                        currentUserId,
                        targetUserId,
                        newRole
                ),
                authorization
        );

        return toResponse(
                savedMember,
                user
        );
    }


    // =====================================================
    // TRANSFER OWNERSHIP
    // =====================================================

    @Transactional
    public void transferOwnership(
            Long chatId,
            Long newOwnerUserId,
            Long currentUserId,
            String authorization
    ) {

        Chat chat = getChat(chatId);

        checkGroup(chat);

        ChatMember currentOwner =
                getMember(
                        chatId,
                        currentUserId
                );

        ChatMember newOwner =
                getMember(
                        chatId,
                        newOwnerUserId
                );

        if (currentOwner.getRole()
                != ChatMemberRole.OWNER) {

            throw new IllegalStateException(
                    "Только OWNER может " +
                            "передать права владельца"
            );
        }

        if (currentUserId.equals(newOwnerUserId)) {
            throw new IllegalArgumentException(
                    "Этот пользователь уже является OWNER"
            );
        }

        newOwner.setRole(
                ChatMemberRole.OWNER
        );

        currentOwner.setRole(
                ChatMemberRole.MEMBER
        );

        chatMemberRepository.save(newOwner);
        chatMemberRepository.save(currentOwner);

        chat.setCreatedBy(newOwnerUserId);

        chatRepository.save(chat);

        chatEventPublisher.publishToChatMembers(
                chatId,
                new GroupChatEvent(
                        "OWNER_CHANGED",
                        chatId,
                        currentUserId,
                        newOwnerUserId,
                        ChatMemberRole.OWNER
                ),
                authorization
        );
    }


    // =====================================================
    // CHECK CAN ADD MEMBER
    // =====================================================

    private void checkCanAddMember(
            ChatMember currentMember
    ) {

        ChatMemberRole role =
                currentMember.getRole();

        if (role != ChatMemberRole.OWNER &&
                role != ChatMemberRole.ADMIN) {

            throw new IllegalStateException(
                    "Недостаточно прав " +
                            "для добавления участника"
            );
        }
    }


    // =====================================================
    // CHECK CAN REMOVE MEMBER
    // =====================================================

    private void checkCanRemoveMember(
            ChatMember currentMember,
            ChatMember targetMember
    ) {

        ChatMemberRole currentRole =
                currentMember.getRole();

        ChatMemberRole targetRole =
                targetMember.getRole();

        if (currentRole == ChatMemberRole.OWNER) {

            if (targetRole == ChatMemberRole.OWNER) {
                throw new IllegalStateException(
                        "Нельзя удалить владельца группы"
                );
            }

            return;
        }

        if (currentRole == ChatMemberRole.ADMIN) {

            if (targetRole != ChatMemberRole.MEMBER) {
                throw new IllegalStateException(
                        "ADMIN может удалить только MEMBER"
                );
            }

            return;
        }

        throw new IllegalStateException(
                "Недостаточно прав " +
                        "для удаления участника"
        );
    }


    public Chat getChat(
            Long chatId
    ) {

        return chatRepository
                .findById(chatId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Чат не найден"
                        )
                );
    }


    public void checkGroup(
            Chat chat
    ) {

        if (chat.getType() != ChatType.GROUP) {
            throw new IllegalArgumentException(
                    "Операция доступна только " +
                            "для группового чата"
            );
        }
    }


    // =====================================================
    // GET MEMBER
    // =====================================================

    public ChatMember getMember(
            Long chatId,
            Long userId
    ) {

        return chatMemberRepository
                .findByChatIdAndUserId(
                        chatId,
                        userId
                )
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Пользователь не является " +
                                        "участником этого чата"
                        )
                );
    }

    private UserProfileResponse getUserById(
            Long userId,
            String authorization
    ) {

        try {

            return userClient.getUserById(
                    userId,
                    authorization
            );

        } catch (FeignException.NotFound e) {

            throw new IllegalArgumentException(
                    "Пользователь не найден: " +
                            userId
            );

        } catch (FeignException e) {

            throw new IllegalStateException(
                    "Не удалось получить пользователя"
            );
        }
    }
    private ChatMemberResponse toResponse(
            ChatMember member,
            UserProfileResponse user
    ) {

        return new ChatMemberResponse(
                member.getUserId(),
                user.username(),
                user.avatar(),
                member.getRole(),
                member.getJoinedAt(),
                member.getLastReadMessageId()
        );
    }
}