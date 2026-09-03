package com.example.messengerservice.service;

import com.example.messengerservice.dto.status.UserStatusEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PresenceService {

    private final SimpMessagingTemplate messagingTemplate;

    private final ConcurrentHashMap<String, Set<String>> userSessions = new ConcurrentHashMap<>();

    public void userConnected(String username, String sessionId) {

        if (username == null || username.isBlank()) {
            return;
        }

        if (sessionId == null || sessionId.isBlank()) {
            return;
        }

        Set<String> sessions = userSessions.computeIfAbsent(username, key -> ConcurrentHashMap.newKeySet());

        boolean wasOffline = sessions.isEmpty();

        sessions.add(sessionId);

        System.out.println("🟢 PRESENCE CONNECT: " + username + " | sessions = " + sessions.size());

        if (wasOffline && sessions.size() == 1) {
            broadcastStatus(username, true);
        }
    }

    public void userDisconnected(String username, String sessionId) {

        if (username == null || username.isBlank()) {
            return;
        }

        if (sessionId == null || sessionId.isBlank()) {
            return;
        }

        Set<String> sessions = userSessions.get(username);

        if (sessions == null) {
            return;
        }

        sessions.remove(sessionId);

        System.out.println("🔴 PRESENCE DISCONNECT: " + username + " | sessions = " + sessions.size());

        if (sessions.isEmpty()) {
            userSessions.remove(username);
            broadcastStatus(username, false);
        }
    }

    public boolean isOnline(String username) {

        Set<String> sessions = userSessions.get(username);

        return sessions != null && !sessions.isEmpty();
    }

    public Set<String> getOnlineUsers() {

        return userSessions.entrySet().stream().filter(entry -> !entry.getValue().isEmpty()).map(entry -> entry.getKey()).collect(Collectors.toSet());
    }

    private void broadcastStatus(String username, boolean online) {

        UserStatusEvent event = new UserStatusEvent(username, online);

        System.out.println("📡 BROADCAST STATUS: " + username + " -> " + (online ? "ONLINE" : "OFFLINE"));

        messagingTemplate.convertAndSend("/topic/user-status", event);
    }
}