package com.example.WebChat.Controller;

import com.example.WebChat.DTO.ChatMessageRequest;
import com.example.WebChat.DTO.CustomPrincipal;
import com.example.WebChat.DTO.MessageAckDTO;
import com.example.WebChat.DTO.WatermarkUpdateEvent;
import com.example.WebChat.Service.MessageService;
import com.example.WebChat.Service.RateLimiterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Controller // Use @Controller for hybrid classes
@RequiredArgsConstructor
public class ChatController {
    private final RateLimiterService rateLimiter;
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
            Authentication authentication,
            @DestinationVariable Long conversationId,
            @Payload ChatMessageRequest request // Changed from String to DTO
    ) {
        if (authentication != null && authentication.getPrincipal() instanceof CustomPrincipal principal) {

            log.info("Received WebSocket message for conversation {}: from user {}", conversationId, principal.username());

            // This saves the message and broadcasts it back to /topic/chat/{conversationId}
            messageService.processAndSend(principal.id(), principal.username(), conversationId, request.content(), request.tempId());
        }
    }

    @MessageMapping("/chat.ack")
    public void processAck(@Payload MessageAckDTO ack, Authentication authentication) {
        if (ack == null || ack.conversationId() == null || ack.messageId() == null || ack.type() == null) {
            log.warn("Ignoring malformed ACK payload: {}", ack);
            return;
        }

        if (authentication == null || !(authentication.getPrincipal() instanceof CustomPrincipal principal)) {
            log.warn("Ignoring ACK without authenticated principal for conversation {}", ack.conversationId());
            return;
        }

        // 2. BROADCAST to the conversation topic
        // This notifies the SENDER (and other members) to turn their ticks blue/grey
        WatermarkUpdateEvent update = new WatermarkUpdateEvent(
                ack.conversationId(),
                principal.id(),
                ack.messageId(),
                ack.type()
        );
        log.debug("ACK received from user {} for chat {} ({})", principal.username(), ack.conversationId(), ack.type());

        messageService.handleMessageAck(update);

        messagingTemplate.convertAndSend("/topic/chat/" + ack.conversationId(), update);
    }

    /**
     * This method is responsible for broadcasting the typing event
     */
    @MessageMapping("/chat/{conversationId}/typing")
    public void handleTyping(@DestinationVariable Long conversationId,
                             @Payload String payload,
                             Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof CustomPrincipal principal) {
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
        messageService.processAndSend(principal.id(), principal.username(), id, body.content(), body.tempId());

        // Return 202 Accepted (Standard for "We're working on it")
        return ResponseEntity.accepted().build();
    }
}
