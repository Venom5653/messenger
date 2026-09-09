package com.example.messengerservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "chats", indexes = {@Index(name = "idx_chats_created_at", columnList = "created_at")})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Chat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChatType type;


    @Column(length = 255)
    private String name;


    @Column( name = "avatar",
            length = 1000)
    private String avatar;


    @Column(name = "created_by", nullable = false)
    private Long createdBy;


    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;


    @Column(name = "last_message_at")
    private LocalDateTime lastMessageAt;


    @OneToMany(mappedBy = "chat", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ChatMember> members = new ArrayList<>();


    @PrePersist
    protected void onCreate() {

        createdAt = LocalDateTime.now();
    }
}