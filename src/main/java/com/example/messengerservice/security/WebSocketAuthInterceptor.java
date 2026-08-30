package com.example.messengerservice.security;

import com.example.messengerservice.service.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    public static final String JWT_ATTRIBUTE = "JWT";

    private final JwtService jwtService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {

        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(
                        message,
                        StompHeaderAccessor.class
                );

        if (accessor == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();

        System.out.println(
                "STOMP COMMAND: " +
                        command +
                        ", user=" +
                        (
                                accessor.getUser() != null
                                        ? accessor.getUser().getName()
                                        : "null"
                        )
        );

        if (StompCommand.CONNECT.equals(command)) {

            String authorization =
                    accessor.getFirstNativeHeader("Authorization");

            if (authorization == null ||
                    !authorization.startsWith("Bearer ")) {

                System.out.println("❌ JWT отсутствует");

                return null;
            }

            String token =
                    authorization.substring(7);

            try {

                if (!jwtService.isTokenValid(token)) {

                    System.out.println("❌ JWT недействителен");

                    return null;
                }

                String username =
                        jwtService.extractUsername(token);

                Long userId =
                        jwtService.extractUserId(token);

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                username,
                                null,
                                List.of()
                        );

                accessor.setUser(authentication);

                accessor.getSessionAttributes().put(
                        JWT_ATTRIBUTE,
                        token
                );

                System.out.println(
                        "✅ WS USER SET: " +
                                username
                );

                System.out.println(
                        "✅ WebSocket AUTHENTICATED: " +
                                username +
                                ", userId=" +
                                userId
                );

                return MessageBuilder.createMessage(
                        message.getPayload(),
                        accessor.getMessageHeaders()
                );

            } catch (Exception e) {

                System.out.println(
                        "❌ JWT ERROR: " +
                                e.getMessage()
                );

                return null;
            }
        }

        return message;
    }
}