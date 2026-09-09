package com.example.messengerservice.repository;

import com.example.messengerservice.entity.Chat;
import com.example.messengerservice.entity.ChatMember;
import com.example.messengerservice.entity.ChatType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatMemberRepository
        extends JpaRepository<ChatMember, Long> {

    Optional<ChatMember> findByChatIdAndUserId(
            Long chatId,
            Long userId
    );

    boolean existsByChatIdAndUserId(
            Long chatId,
            Long userId
    );

    Page<ChatMember> findByUserId(
            Long userId,
            Pageable pageable
    );

    Page<ChatMember> findByChatId(
            Long chatId,
            Pageable pageable
    );

    @Query("""
        select cm
        from ChatMember cm
        join fetch cm.chat c
        where cm.userId = :userId
        order by
            case
                when c.lastMessageAt is null
                then c.createdAt
                else c.lastMessageAt
            end desc
        """)
    List<ChatMember> findAllChatsByUserId(
            @Param("userId") Long userId
    );

    List<ChatMember> findAllByChatIdIn(
            List<Long> chatIds
    );

    List<ChatMember> findAllByChatId(
            Long chatId
    );

    long countByChatId(
            Long chatId
    );

    void deleteByChatIdAndUserId(
            Long chatId,
            Long userId
    );

    @Query("""
            select cm1.chat
            from ChatMember cm1
            join ChatMember cm2
              on cm2.chat = cm1.chat
            where cm1.userId = :user1Id
              and cm2.userId = :user2Id
              and cm1.chat.type = :type
              and 2 = (
                  select count(cm3)
                  from ChatMember cm3
                  where cm3.chat = cm1.chat
              )
            """)
    Optional<Chat> findPrivateChatBetweenUsers(
            @Param("user1Id") Long user1Id,
            @Param("user2Id") Long user2Id,
            @Param("type") ChatType type
    );

    void deleteAllByChatId(
            Long chatId
    );
    @Query("""
        select cm.chat.id, count(m)
        from ChatMember cm
        left join Message m
            on m.chat.id = cm.chat.id
            and m.senderId <> :userId
            and (
                cm.lastReadMessageId is null
                or m.id > cm.lastReadMessageId
            )
        where cm.userId = :userId
        group by cm.chat.id
        """)
    List<Object[]> countUnreadMessagesByUser(
            @Param("userId") Long userId
    );
}