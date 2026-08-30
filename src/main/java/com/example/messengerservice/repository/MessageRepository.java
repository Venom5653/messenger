package com.example.messengerservice.repository;

import com.example.messengerservice.entity.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MessageRepository extends JpaRepository<Message, Long> {

    List<Message> findBySenderIdAndRecipientId(Long senderId, Long recipientId);


    List<Message> findByRecipientId(Long recipientId);


    List<Message> findByChatRoomIdOrderByCreatedAtAsc(Long chatRoomId);


    Optional<Message> findTopByChatRoomIdOrderByCreatedAtDesc(Long chatRoomId);


    long countByChatRoomIdAndRecipientIdAndReadFalse(Long chatRoomId, Long recipientId);

    @Modifying
    @Query("""
    UPDATE Message m
    SET m.read = true
    WHERE m.chatRoom.id = :chatId
      AND m.recipientId = :userId
      AND m.read = false
""")
    void markMessagesAsRead(
            @Param("chatId") Long chatId,
            @Param("userId") Long userId
    );

    @Query("""
                select m
                from Message m
                where m.chatRoom.id = :chatRoomId
                order by m.id desc
            """)
    List<Message> findLatestMessages(@Param("chatRoomId") Long chatRoomId, Pageable pageable);

    @Query("""
                select m
                from Message m
                where m.chatRoom.id = :chatRoomId
                  and m.id < :beforeId
                order by m.id desc
            """)
    List<Message> findMessagesBefore(
            @Param("chatRoomId") Long chatRoomId,
            @Param("beforeId") Long beforeId, Pageable pageable);

    @Query("""
    select m
    from Message m
    where m.chatRoom.id = :chatId
      and m.recipientId = :userId
      and m.read = false
    order by m.id asc
""")
    List<Message> findUnreadMessages(
            @Param("chatId") Long chatId,
            @Param("userId") Long userId
    );
}