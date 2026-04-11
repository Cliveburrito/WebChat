package com.example.WebChat.message;

import com.example.WebChat.auth.dto.CustomPrincipal;
import com.example.WebChat.Service.RateLimiterService;
import com.example.WebChat.message.dto.ChatMessageRequest;
import com.example.WebChat.message.dto.ChatMessageResponse;
import com.example.WebChat.message.dto.MessageAckRequest;
import com.example.WebChat.message.dto.MessageEditRequest;
import com.example.WebChat.message.dto.MessageReactionEvent;
import com.example.WebChat.message.dto.MessageReactionRequest;
import com.example.WebChat.message.dto.WatermarkUpdateEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@Controller // Use @Controller for hybrid classes
@RequiredArgsConstructor
public class ChatController {
    private final RateLimiterService rateLimiter;
    private final MessageService messageService;
    private final MessageQueryService messageQueryService;
    private final MessageReactionService messageReactionService;
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
            Authentication authentication,
            @DestinationVariable Long conversationId,
            @Payload ChatMessageRequest request // Changed from String to DTO
    ) {
        if (authentication != null && authentication.getPrincipal() instanceof CustomPrincipal principal) {

            log.info("Received WebSocket message for conversation {}: from user {}", conversationId, principal.username());

            // This saves the message and broadcasts it back to /topic/chat/{conversationId}
            messageService.processAndSend(principal.id(), conversationId, request.content(), request.tempId(), request.replyToMessageId());
        }
    }

    @MessageMapping("/chat.ack")
    public void processAck(
            @Payload MessageAckRequest ack,
            Authentication authentication) {

        if (authentication == null || !(authentication.getPrincipal() instanceof CustomPrincipal principal)) {
            log.error("Principal is NULL in processAck! Connection not authenticated properly.");
            return;
        }

        log.info("ACK RECEIVED from user: {} (ID: {})", principal.getUsername(), principal.getUserId());

        WatermarkUpdateEvent update = new WatermarkUpdateEvent(
                ack.conversationId(),
                principal.getUserId(),
                ack.messageId(),
                ack.type()
        );

        if (messageService.handleMessageAck(update)) {
            messagingTemplate.convertAndSend("/topic/chat/" + ack.conversationId(), update);
        }
    }

    /**
     * This method is responsible for broadcasting the typing event
     */
    @MessageMapping("/chat/{conversationId}/typing")
    public void handleTyping(@DestinationVariable Long conversationId,
                             @Payload String payload,
                             Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof CustomPrincipal principal) {
            if (!messageService.canAccessConversation(principal.id(), conversationId)) {
                log.warn("Rejected typing signal from non-member user {} for chat {}", principal.username(), conversationId);
                return;
            }
            String typingSignal = "__STOP__".equals(payload) ? "__STOP__" : principal.username();

            messagingTemplate.convertAndSend("/topic/chat/" + conversationId + "/typing", typingSignal);
            log.debug("Typing signal from {} in chat {}", principal.username(), conversationId);
        }
    }

    /**
     * This is what my frontend currently uses to send a message, this endpoint not the socket
     */
    @PostMapping("/api/messages/chat/{id}/smsg")
    public ResponseEntity<Void> sendMessage(
            @AuthenticationPrincipal CustomPrincipal principal,
            @PathVariable Long id,
            @RequestBody ChatMessageRequest body // Use the Request DTO (content + tempId)
    ) {
        rateLimiter.consumeMessageOrThrow(principal.id(), principal.username(), id);
        // Kick off the Async flow
        messageService.processAndSend(principal.id(), id, body.content(), body.tempId(), body.replyToMessageId());

        // Return 202 Accepted (Standard for "We're working on it")
        return ResponseEntity.accepted().build();
    }

    @GetMapping({"/chat/{conversationId}/search", "/api/messages/chat/{conversationId}/search"})
    public ResponseEntity<List<ChatMessageResponse>> searchMessage(
            @AuthenticationPrincipal CustomPrincipal principal,
            @PathVariable Long conversationId,
            @RequestParam String query
    ) {
        List<ChatMessageResponse> results = messageQueryService.searchInChat(principal.getUserId(), conversationId, query);

        return ResponseEntity.ok(results);
    }

    @PostMapping("/api/messages/{messageId}/reactions")
    public ResponseEntity<List<MessageReactionEvent>> toggleReaction(
            @AuthenticationPrincipal CustomPrincipal principal,
            @PathVariable Long messageId,
            @RequestBody MessageReactionRequest request
    ) {
        List<MessageReactionEvent> events = messageReactionService.toggleReaction(principal.id(), messageId, request.emoji());
        events.forEach(event -> messagingTemplate.convertAndSend("/topic/chat/" + event.conversationId(), event));
        return ResponseEntity.ok(events);
    }

    @PatchMapping("/api/messages/{messageId}")
    public ResponseEntity<ChatMessageResponse> editMessage(
            @AuthenticationPrincipal CustomPrincipal principal,
            @PathVariable Long messageId,
            @RequestBody MessageEditRequest request
    ) {
        return ResponseEntity.ok(messageService.editMessage(principal.id(), messageId, request.content()));
    }

    @DeleteMapping("/api/messages/{messageId}")
    public ResponseEntity<ChatMessageResponse> deleteMessage(
            @AuthenticationPrincipal CustomPrincipal principal,
            @PathVariable Long messageId
    ) {
        return ResponseEntity.ok(messageService.deleteMessage(principal.id(), messageId));
    }
}
