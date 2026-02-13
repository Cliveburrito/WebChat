package com.example.WebChat.Controller;

import com.example.WebChat.Service.PresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.Set;

@Controller
@Slf4j
@RequiredArgsConstructor
public class PresenceEventListener {
    private final SimpMessagingTemplate messagingTemplate;
    private final PresenceService presenceService;

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectEvent event) {
        StompHeaderAccessor sha = StompHeaderAccessor.wrap(event.getMessage());
        if (sha.getUser() != null) {
            presenceService.onConnect(sha.getUser().getName());
        }
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor sha = StompHeaderAccessor.wrap(event.getMessage());
        if (sha.getUser() != null) {
            presenceService.onDisconnect(sha.getUser().getName());
        }
    }

    @MessageMapping("/presence/sync")
    public void syncPresence(Principal principal) {
        if (principal == null) return;

        String username = principal.getName();
        log.info("User {} is syncing their presence list", username);

        // 1. Get the global list from Redis
        Set<String> currentOnlineUsers = presenceService.getOnlineUsers();

        // 2. Send it ONLY to this specific user
        messagingTemplate.convertAndSendToUser(
                username,
                "/topic/public/presence",
                currentOnlineUsers
        );
    }

    @MessageMapping("/presence/heartbeat")
    public void handleHeartbeat(Principal principal) {
        if (principal != null) {
            presenceService.updateHeartbeat(principal.getName());
        }
    }
}