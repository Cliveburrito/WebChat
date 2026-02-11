package com.example.WebChat.Controller;

import com.example.WebChat.DTO.ChatMessageResponse;
import com.example.WebChat.Service.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

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
            @Payload String content
    ) {
        // Fallback for security, principal should not be null if configured correctly
        String username = (principal != null) ? principal.getName() : "anonymous";

        log.info("Received WebSocket message for conversation {}: from user {}", conversationId, username);

        // This saves the message and broadcasts it back to /topic/chat/{conversationId}
        messageService.processAndSend(username, conversationId, content);
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
    @PostMapping("/api/messages/chat/{id}/smsg") // Move full path here
    @ResponseBody // Required since we removed @RestController
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
    public ResponseEntity<Page<ChatMessageResponse>> getChatHistory(
            Principal principal,
            @PathVariable Long conversationId,
            @PageableDefault(size = 20, sort = "sentAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        String username = principal.getName();
        log.info("User {} requesting history for conversation {}", username, conversationId);

        // Change the service call to accept pageable
        Page<ChatMessageResponse> history = messageService.getChatHistory(conversationId, pageable);
        return ResponseEntity.ok(history);
    }
}