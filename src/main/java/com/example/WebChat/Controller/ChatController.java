package com.example.WebChat.Controller;

import com.example.WebChat.DTO.ChatMessageResponse;
import com.example.WebChat.Repository.MessageRepository;
import com.example.WebChat.Service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;


import java.security.Principal;
import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
@RequestMapping("/api")
public class ChatController {

    private final MessageService messageService;
    private final MessageRepository messageRepository;

    // *** FRONTEND sends message to /app/chat/{id} ***
    @MessageMapping("/chat/{conversationId}")
    public void sendMessage(
            Principal principal,
            @DestinationVariable Long conversationId,
            @Payload String content
    ) {
        String username;
        if (principal != null) {
            username = principal.getName();
        } else {
            username = "anonymous"; // fallback για τώρα
        }

        System.out.println("WS message: convo=" + conversationId +
                ", from=" + username + ", content=" + content);

        messageService.processAndSend(username, conversationId, content);
    }

    @PostMapping("/chat/{id}/smsg")
    public ResponseEntity<ChatMessageResponse> sendMessage(
            @PathVariable Long id,
            @RequestBody ChatMessageResponse response
    ) {

        messageService.postMessage(response.senderUsername(),
                response.conversationId(),
                response.content());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/chats/{id}/messages")
    public ResponseEntity<?> getHistory(@PathVariable Long id) {
        System.out.println(">>> Controller reached for ID: " + id); // Debug print

        try {
            List<ChatMessageResponse> history = messageService.getChatHistory(id);
            return ResponseEntity.ok(history);

        } catch (RuntimeException e) {
            // This ensures you see the EXACT error message in Postman
            System.err.println(">>> ERROR: " + e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }
}
