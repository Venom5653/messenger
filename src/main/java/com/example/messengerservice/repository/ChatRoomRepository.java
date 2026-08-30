package com.example.messengerservice.repository;

import com.example.messengerservice.entity.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChatRoomRepository
        extends JpaRepository<ChatRoom, Long> {

    Optional<ChatRoom>
    findByUser1IdAndUser2Id(
            Long user1Id,
            Long user2Id
    );

    List<ChatRoom>
    findByUser1IdOrUser2Id(
            Long user1Id,
            Long user2Id
    );
}