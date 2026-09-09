package com.example.messengerservice.repository;

import com.example.messengerservice.entity.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MessageRepository
        extends JpaRepository<Message, Long> {

    @Query("""
            select m
            from Message m
            where m.chat.id = :chatId
            order by m.id desc
            """)
    List<Message> findLatestMessages(
            @Param("chatId") Long chatId,
            Pageable pageable
    );

    @Query("""
            select m
            from Message m
            where m.chat.id = :chatId
              and m.id < :beforeId
            order by m.id desc
            """)
    List<Message> findMessagesBefore(
            @Param("chatId") Long chatId,
            @Param("beforeId") Long beforeId,
            Pageable pageable
    );

    Optional<Message> findTopByChatIdOrderByIdDesc(
            Long chatId
    );

    @Query("""
            select count(m)
            from Message m
            where m.chat.id = :chatId
              and m.senderId <> :userId
            """)
    long countUnreadMessagesFromBeginning(
            @Param("chatId") Long chatId,
            @Param("userId") Long userId
    );

    @Query("""
            select count(m)
            from Message m
            where m.chat.id = :chatId
              and m.id > :lastReadMessageId
              and m.senderId <> :userId
            """)
    long countUnreadMessages(
            @Param("chatId") Long chatId,
            @Param("lastReadMessageId") Long lastReadMessageId,
            @Param("userId") Long userId
    );

    void deleteAllByChatId(Long chatId);

    @Query("""
        select m
        from Message m
        where m.id in (
            select max(m2.id)
            from Message m2
            where m2.chat.id in :chatIds
            group by m2.chat.id
        )
        """)
    List<Message> findLastMessagesByChatIds(
            @Param("chatIds") List<Long> chatIds
    );
}