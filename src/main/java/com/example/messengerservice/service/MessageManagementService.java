package com.example.messengerservice.service;

import com.example.messengerservice.repository.MessageRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class MessageManagementService {

    private final MessageRepository messageRepository;

    public void deleteMessage(Long id){
        messageRepository.deleteById(id);
    }
}
