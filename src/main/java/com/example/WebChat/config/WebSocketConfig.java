package com.example.WebChat.config;

import com.example.WebChat.auth.dto.CustomPrincipal;
import com.example.WebChat.auth.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.Collection;

/**
 * WebSocket configuration class for setting up STOMP over WebSocket.
 *
 * <p>This class:
 * <ul>
 *   <li>Configures a simple in-memory message broker.</li>
 *   <li>Registers STOMP endpoints that clients can connect to.</li>
 *   <li>Adds a channel interceptor to authenticate WebSocket connections
 *       using a JWT sent in the {@code Authorization} header.</li>
 * </ul>
 * </p>
 */
@Slf4j
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private final JwtService jwtService;
    private final AppProperties appProperties;

    /**
     * Configures the message broker, which is responsible for routing messages
     * between clients and the server.
     *
     * <ul>
     *   <li>{@code enableSimpleBroker("/topic")} registers a simple in-memory broker
     *       that clients can subscribe to under the {@code /topic} destination.</li>
     *   <li>{@code setApplicationDestinationPrefixes("/app")} defines the prefix for
     *       messages that are bound for @MessageMapping methods on the server side.</li>
     * </ul>
     *
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue");

        // Clients send messages to /app/... which are handled by @MessageMapping methods in @Controller classes
        config.setApplicationDestinationPrefixes("/app");
    }

    /**
     * Registers STOMP endpoints that clients use to establish WebSocket connections.
     *
     * <p>
     * The endpoint {@code /ws} is exposed, and SockJS fallback is enabled so that
     * clients without native WebSocket support can still connect.
     * </p>

     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        String[] allowedOrigins = appProperties.getWebsocket()
                .getAllowedOrigins()
                .toArray(String[]::new);

        registry.addEndpoint("/ws")
                .setAllowedOrigins(allowedOrigins)
                .withSockJS()
                .setHeartbeatTime(10000);
    }

    /**
     * Configures the channel used for inbound messages from clients.
     *
     * <p>
     * Here we register a {@link ChannelInterceptor} to:
     * <ul>
     *   <li>Intercept STOMP {@code CONNECT} frames.</li>
     *   <li>Extract and validate the JWT from the {@code Authorization} header.</li>
     *   <li>Set the authenticated {@link Authentication} principal on the WebSocket session.</li>
     * </ul>
     * </p>
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor =
                        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

                if (accessor == null) return message;

                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    accessor.setUser(authenticate(accessor));
                }

                return message;
            }
        });
    }

    private Authentication authenticate(StompHeaderAccessor accessor) {
        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new MessageDeliveryException("Missing Authorization header");
        }

        String token = authHeader.substring(7);

        if (!jwtService.isTokenValid(token)) {
            throw new MessageDeliveryException("Invalid JWT");
        }

        String username = jwtService.extractUsername(token);
        Long userId = jwtService.extractUserId(token);
        var authorities = jwtService.extractAuthorities(token);

        Authentication auth = getAuthentication(username, userId, authorities);
        log.info("WebSocket authenticated: {} with ID {}", username, userId);
        return auth;
    }

    private static Authentication getAuthentication(String username, Long userId, Collection<? extends GrantedAuthority> authorities) {
        if (username == null || userId == null) {
            throw new MessageDeliveryException("Invalid JWT claims");
        }

        CustomPrincipal principal = new CustomPrincipal(
                userId,
                username,
                null,   // password hash όχι εδώ
                false,  // stealth mode
                true,   // enabled
                authorities
        );

        return new UsernamePasswordAuthenticationToken(
                principal,
                null,
                authorities
        );
    }
}
