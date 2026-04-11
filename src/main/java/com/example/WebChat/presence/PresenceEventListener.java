package com.example.WebChat.presence;

import com.example.WebChat.auth.dto.CustomPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import org.springframework.stereotype.Controller;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.security.core.Authentication;

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
        // We need the CustomPrincipal from the connect event!
        UsernamePasswordAuthenticationToken auth = (UsernamePasswordAuthenticationToken) sha.getHeader("simpUser");

        if (auth != null && auth.getPrincipal() instanceof CustomPrincipal principal) {
            presenceService.onConnect(principal.username());

            messagingTemplate.convertAndSendToUser(
                    principal.username(),
                    "/topic/public/presence",
                    presenceService.getOnlineUsers()
            );
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
    public void syncPresence(Authentication authentication) {
        if (authentication == null) return;

        Object principalObj = authentication.getPrincipal();

        if (principalObj instanceof CustomPrincipal customPrincipal) {

            String username = customPrincipal.username();
            log.info("User {} is syncing their presence list", username);

            Set<String> currentOnlineUsers = presenceService.getOnlineUsers();

            messagingTemplate.convertAndSendToUser(
                    username,
                    "/topic/public/presence",
                    currentOnlineUsers
            );
        }
    }

    @MessageMapping("/presence/heartbeat")
    public void handleHeartbeat(Authentication authentication) { // 👈 Αλλαγή εδώ
        if (authentication != null && authentication.getPrincipal() instanceof CustomPrincipal principal) {

            presenceService.updateHeartbeat(principal.username());
        } else {
            log.warn("Heartbeat received but no authenticated user found in context");
        }
    }
}
