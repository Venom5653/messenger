package com.example.messengerservice.repository;

import com.example.messengerservice.entity.Chat;
import com.example.messengerservice.entity.ChatType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatRepository
        extends JpaRepository<Chat, Long> {

    Page<Chat> findByType(
            ChatType type,
            Pageable pageable
    );
}