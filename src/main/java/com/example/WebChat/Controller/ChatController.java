package com.example.WebChat.Controller;

import com.example.WebChat.DTO.ChatMessageRequest;
import com.example.WebChat.DTO.ChatMessageResponse;
import com.example.WebChat.Service.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@Slf4j
@Controller // Use @Controller for hybrid classes
@RequiredArgsConstructor
public class ChatController {

    private final MessageService messageService;
    private final SimpMessagingTemplate messagingTemplate;
    /**
     * WebSocket Endpoint: Handles messages sent to /app/chat/{conversationId}
     * The @MessageMapping prefix (/app) is defined in WebSocketConfig.
     * <p>
     * FOR NOW, I AM NOT USING THIS IN THE FRONTEND I AM PLAYING WITH A HYBRID MODEL
     * I USE REST FOR SENDING A MESSAGE AND A WEBSOCKET FOR RECEIVING THE MESSAGES, I LL STILL KEEP IT
     * IN CASE I WANT TO SWAP TO PURE SOCKET INTERACTION.
     */
    @MessageMapping("/chat/{conversationId}")
    public void handleWebSocketMessage(
            Principal principal,
            @DestinationVariable Long conversationId,
            @Payload ChatMessageRequest request // Changed from String to DTO
    ) {
        // Fallback for security, principal should not be null if configured correctly
        String username = (principal != null) ? principal.getName() : "anonymous";

        log.info("Received WebSocket message for conversation {}: from user {}", conversationId, username);

        // This saves the message and broadcasts it back to /topic/chat/{conversationId}
        messageService.processAndSend(username, conversationId, request.content(), request.tempId());
    }

    /**
     * This method is responsible for broadcasting the typing event
     */
    @MessageMapping("/chat/{conversationId}/typing")
    public void handleTyping(@DestinationVariable Long conversationId,
                             Principal principal) {
        String username = principal.getName();
        messagingTemplate.convertAndSend("/topic/chat/" + conversationId + "/typing", username);
        log.info("Typing...");
    }


    /**
     * This is what my frontend currently uses to send a message, this endpoint not the socket
     */
    @PostMapping("/api/messages/chat/{id}/smsg")
    public ResponseEntity<Void> sendMessage(
            Principal principal,
            @PathVariable Long id,
            @RequestBody ChatMessageRequest body // Use the Request DTO (content + tempId)
    ) {
        // Kick off the Async flow
        messageService.processAndSend(principal.getName(), id, body.content(), body.tempId());

        // Return 202 Accepted (Standard for "We're working on it")
        return ResponseEntity.accepted().build();
    }


    /**
     * rest endpoint that retrieves message history for a specific conversation.
     */
//    @GetMapping("/{conversationId}/history")
//    public ResponseEntity<List<ChatMessageResponse>> getChatHistory(
//            @PathVariable Long conversationId,
//            @RequestParam(defaultValue = "0") int page,
//            @RequestParam(defaultValue = "20") int size,
//            Principal principal) {
//
//        // The service handles the SecurityContext internally, so we don't need Principal here
//        List<ChatMessageResponse> history = messageService.getChatHistory(conversationId, page, size);
//        log.info("Use {} requests chat history for conversation with id: {}", principal.getName() , conversationId);
//        return ResponseEntity.ok(history);
//    }
}