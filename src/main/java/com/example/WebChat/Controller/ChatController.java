package com.example.WebChat.Controller;

import com.example.WebChat.DTO.ChatMessageResponse;
import com.example.WebChat.Service.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/messages") // base path for REST endpoints
public class ChatController {

    private final MessageService messageService;

    /**
     * WebSocket Endpoint: Handles messages sent to /app/chat/{conversationId}
     * The @MessageMapping prefix (/app) is defined in WebSocketConfig.
     *
     * FOR NOW, I AM NOT USING THIS IN THE FRONTEND I AM PLAYING WITH A HYBRID MODEL
     * I USE REST FOR SENDING A MESSAGE AND A WEBSOCKET FOR RECEIVING THE MESSAGES, I LL STILL KEEP IT
     * IN CASE I WANT TO SWAP TO PURE SOCKET INTERACTION.
     */
    @MessageMapping("/chat/{conversationId}")
    public void handleWebSocketMessage(
            Principal principal,
            @DestinationVariable Long conversationId,
            @Payload String content
    ) {
        // Fallback for security, principal should not be null if configured correctly
        String username = (principal != null) ? principal.getName() : "anonymous";

        log.info("Received WebSocket message for conversation {}: from user {}", conversationId, username);

        // This saves the message and broadcasts it back to /topic/chat/{conversationId}
        messageService.processAndSend(username, conversationId, content);
    }


    /**This is what my frontend currently uses to send a message, this endpoint not the socket
     */
    @PostMapping("/chat/{id}/smsg")
    public ResponseEntity<ChatMessageResponse> sendMessage(
            Principal principal,
            @PathVariable Long id,
            @RequestBody ChatMessageResponse body
    ) {
        ChatMessageResponse saved = messageService.processAndSend(principal.getName(), id, body.content());
        return ResponseEntity.ok(saved);
    }


    /**
     * rest endpoint that retrieves message history for a specific conversation.
     */
    @GetMapping("/history/{conversationId}")
    public ResponseEntity<List<ChatMessageResponse>> getChatHistory(
            Principal principal,
            @PathVariable Long conversationId
    ) {
        String username = principal.getName();
        log.info("User {} requesting history for conversation {}", username, conversationId);

        List<ChatMessageResponse> history = messageService.getChatHistory(conversationId, username);
        return ResponseEntity.ok(history);
    }
}