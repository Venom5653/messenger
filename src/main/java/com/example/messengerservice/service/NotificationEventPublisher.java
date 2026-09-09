package com.example.messengerservice.service;

import com.example.messengerservice.config.RabbitMQConfig;

import com.example.messengerservice.dto.chat.ChatStateEvent;
import com.example.messengerservice.dto.messenges.MessageSentEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publishMessageSent(MessageSentEvent event) {

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE,
                RabbitMQConfig.ROUTING_KEY,
                event
        );

        System.out.println(
                "RabbitMQ: MESSAGE_SENT опубликован, messageId="
                        + event.messageId()
        );
    }
    public void publishChatOpened(
            String username,
            Long chatId
    ) {

        ChatStateEvent event =
                new ChatStateEvent(
                        "CHAT_OPENED",
                        username,
                        chatId
                );

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE,
                RabbitMQConfig.CHAT_STATE_ROUTING_KEY,
                event
        );

        System.out.println(
                "RabbitMQ: CHAT_OPENED опубликован, user="
                        + username
                        + ", chatId="
                        + chatId
        );
    }

    public void publishChatClosed(
            String username,
            Long chatId
    ) {

        ChatStateEvent event =
                new ChatStateEvent(
                        "CHAT_CLOSED",
                        username,
                        chatId
                );

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE,
                RabbitMQConfig.CHAT_STATE_ROUTING_KEY,
                event
        );

        System.out.println(
                "RabbitMQ: CHAT_CLOSED опубликован, user="
                        + username
                        + ", chatId="
                        + chatId
        );
    }
}