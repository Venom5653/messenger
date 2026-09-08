package com.example.messengerservice.service;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ActiveChatService {

    private final Map<String, Long> activeChats = new ConcurrentHashMap<>();

    public void openChat(String username, Long chatId) {

        if (username == null || chatId == null) {
            return;
        }

        activeChats.put(username, chatId);

        System.out.println("CHAT OPENED: user=" + username + ", chatId=" + chatId);
    }

    public void closeChat(String username) {

        if (username == null) {
            return;
        }

        activeChats.remove(username);

        System.out.println("CHAT CLOSED: user=" + username);
    }

    public boolean isChatOpen(String username, Long chatId) {

        if (username == null || chatId == null) {
            return false;
        }

        return chatId.equals(activeChats.get(username));
    }

    public Long getActiveChat(String username) {

        if (username == null) {
            return null;
        }

        return activeChats.get(username);
    }
}