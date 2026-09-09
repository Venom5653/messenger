package com.example.messengerservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;


@Entity
@Table(
        name = "chat_members",
        uniqueConstraints = {
                @UniqueConstraint(
                        columnNames = {
                                "chat_id",
                                "user_id"
                        }
                )
        },
        indexes = {
                @Index(
                        name = "idx_chat_members_user",
                        columnList = "user_id"
                ),
                @Index(
                        name = "idx_chat_members_chat",
                        columnList = "chat_id"
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMember {

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
            name = "user_id",
            nullable = false
    )
    private Long userId;


    @Enumerated(EnumType.STRING)
    @Column(
            nullable = false
    )
    @Builder.Default
    private ChatMemberRole role =
            ChatMemberRole.MEMBER;


    @Column(
            name = "joined_at",
            nullable = false
    )
    private LocalDateTime joinedAt;


    @Column(
            name = "last_read_message_id"
    )
    private Long lastReadMessageId;


    @PrePersist
    protected void onCreate() {

        joinedAt =
                LocalDateTime.now();
    }
}
