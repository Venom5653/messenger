package com.example.messengerservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "messages")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(
            name = "sender_id",
            nullable = false
    )
    private Long senderId;

    @Column(
            name = "recipient_id",
            nullable = false
    )
    private Long recipientId;

    @Column(
            nullable = false,
            columnDefinition = "TEXT"
    )
    private String content;

    @Column(
            nullable = false
    )
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "chat_room_id",
            nullable = false
    )
    private ChatRoom chatRoom;

    @Column(nullable = false)
    @Builder.Default
    private boolean read = false;


    @PrePersist
    protected void onCreate() {

        createdAt =
                LocalDateTime.now();
    }
}