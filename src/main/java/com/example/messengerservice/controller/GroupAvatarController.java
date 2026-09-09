package com.example.messengerservice.controller;

import com.example.messengerservice.dto.chat.ChatResponse;
import com.example.messengerservice.service.GroupChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
@RequestMapping("/api/chats")
@RequiredArgsConstructor
public class GroupAvatarController {

    private final GroupChatService groupChatService;

    @PostMapping(value = "/{chatId}/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ChatResponse> updateGroupAvatar(@PathVariable Long chatId, @RequestParam("file") MultipartFile file, @RequestHeader("Authorization") String authorization, @RequestHeader("X-User-Id") Long currentUserId) {

        return ResponseEntity.ok(groupChatService.updateGroupAvatar(chatId, file, currentUserId, authorization));
    }

    @DeleteMapping("/{chatId}/avatar")
    public ResponseEntity<ChatResponse> deleteGroupAvatar(@PathVariable Long chatId, @RequestHeader("Authorization") String authorization, @RequestHeader("X-User-Id") Long currentUserId) {

        return ResponseEntity.ok(groupChatService.deleteGroupAvatar(chatId, currentUserId, authorization));
    }

    @GetMapping("/avatar/{fileName}")
    public ResponseEntity<Resource> getGroupAvatar(@PathVariable String fileName) {

        try {

            if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {

                return ResponseEntity.badRequest().build();
            }

            Path uploadDirectory = Paths.get("uploads", "group-avatars").toAbsolutePath().normalize();

            Path filePath = uploadDirectory.resolve(fileName).normalize();

            if (!filePath.startsWith(uploadDirectory)) {

                return ResponseEntity.badRequest().build();
            }

            Resource resource = new UrlResource(filePath.toUri());

            if (!resource.exists() || !resource.isReadable()) {

                return ResponseEntity.notFound().build();
            }

            String contentType = Files.probeContentType(filePath);

            if (contentType == null) {

                contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
            }

            return ResponseEntity.ok().contentType(MediaType.parseMediaType(contentType)).body(resource);

        } catch (Exception e) {

            return ResponseEntity.internalServerError().build();
        }
    }
}