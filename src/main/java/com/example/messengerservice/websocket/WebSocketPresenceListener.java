package com.example.messengerservice.websocket;

import com.example.messengerservice.service.PresenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
@RequiredArgsConstructor
public class WebSocketPresenceListener {

    private final PresenceService presenceService;


    // =====================================================
    // CONNECT
    // =====================================================

    @EventListener
    public void handleConnected(
            SessionConnectedEvent event
    ) {

        StompHeaderAccessor accessor =
                StompHeaderAccessor.wrap(
                        event.getMessage()
                );


        if (accessor.getUser() == null) {

            System.out.println(
                    "❌ CONNECT WITHOUT USER"
            );

            return;
        }


        String username =
                accessor.getUser().getName();


        String sessionId =
                accessor.getSessionId();


        System.out.println(
                "🟢 USER CONNECTED: "
                        + username
                        + " | sessionId = "
                        + sessionId
        );


        presenceService.userConnected(
                username,
                sessionId
        );
    }


    // =====================================================
    // DISCONNECT
    // =====================================================

    @EventListener
    public void handleDisconnected(
            SessionDisconnectEvent event
    ) {

        StompHeaderAccessor accessor =
                StompHeaderAccessor.wrap(
                        event.getMessage()
                );


        if (accessor.getUser() == null) {

            System.out.println(
                    "❌ DISCONNECT WITHOUT USER"
            );

            return;
        }


        String username =
                accessor.getUser().getName();


        String sessionId =
                accessor.getSessionId();


        System.out.println(
                "🔴 USER DISCONNECTED: "
                        + username
                        + " | sessionId = "
                        + sessionId
        );


        presenceService.userDisconnected(
                username,
                sessionId
        );
    }
}