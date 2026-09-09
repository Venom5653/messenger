package com.example.messengerservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "messages",
        indexes = {

                @Index(
                        name = "idx_messages_chat_created",
                        columnList = "chat_id, created_at"
                ),

                @Index(
                        name = "idx_messages_sender",
                        columnList = "sender_id"
                )
        }
)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Message {

    @Id
    @GeneratedValue(
            strategy = GenerationType.IDENTITY
    )
    private Long id;


    @ManyToOne(
            fetch = FetchType.LAZY
    )
    @JoinColumn(
            name = "chat_id",
            nullable = false
    )
    private Chat chat;


    @Column(
            name = "sender_id",
            nullable = false
    )
    private Long senderId;


    @Column(
            nullable = false,
            columnDefinition = "TEXT"
    )
    private String content;


    @Column(
            name = "created_at",
            nullable = false
    )
    private LocalDateTime createdAt;


    @PrePersist
    protected void onCreate() {

        createdAt =
                LocalDateTime.now();
    }
}