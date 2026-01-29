package com.example.WebChat.Controller;

import lombok.Getter;
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
import java.util.concurrent.ConcurrentHashMap;

@Controller
@Slf4j
@RequiredArgsConstructor
public class PresenceEventListener {

    private final SimpMessagingTemplate messagingTemplate;
    // Kinda like a cache, some day I'll use Redis xD
    @Getter
    private static final Set<String> onlineUsers = ConcurrentHashMap.newKeySet();

    /**
     * A method that "hears" for a connection to the Socket
     * and broadcasts it so the frontend can switch the green light on!
     */
    @EventListener
    public void handleWebSocketConnectListener(SessionConnectEvent event) {
        StompHeaderAccessor sha = StompHeaderAccessor.wrap(event.getMessage());
        Principal principal = sha.getUser();
        if (principal != null) {
            String username = principal.getName();
            onlineUsers.add(username);
            log.info("User Connected: {}", username);
            // Broadcast the updated list to everyone subscribed
            messagingTemplate.convertAndSend("/topic/public/presence", onlineUsers);
        }
    }

    /**
     * A method that "hears" for a user disconnecting from the Socket
     * and broadcasts it so the frontend can switch the green light off!
     */
    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor sha = StompHeaderAccessor.wrap(event.getMessage());
        Principal principal = sha.getUser();
        if (principal != null) {
            String username = principal.getName();
            onlineUsers.remove(username);
            log.info("User Disconnected: {}", username);
            // Broadcast the updated list to everyone
            messagingTemplate.convertAndSend("/topic/public/presence", onlineUsers);
        }
    }

    /**
     * We sent the list of users that are online to the user that asked for it
     */
    @MessageMapping("/presence/sync")
    public void syncPresence(Principal principal) {
        if (principal == null) return;

        String username = principal.getName();
        log.info("User {} requested presence sync", username);

        // We send the current list ONLY to the person who asked
        messagingTemplate.convertAndSendToUser(
                principal.getName(),
                "/topic/public/presence",
                PresenceEventListener.getOnlineUsers()
        );
    }

}