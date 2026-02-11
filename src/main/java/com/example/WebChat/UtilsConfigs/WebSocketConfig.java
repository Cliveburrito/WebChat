package com.example.WebChat.UtilsConfigs;

import com.example.WebChat.Service.JwtService;
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
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

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
        // Clients subscribe to destinations like /topic/room1
        config.enableSimpleBroker("/topic");

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
        // verify that endpoint registration runs during startup
        log.info("registerStompEndpoints CALLED!");

        // WebSocket endpoint: ws://<host>/ws with SockJS fallback
        // client needs to connect for the webSocket handshake
        registry.addEndpoint("/ws").
                setAllowedOrigins("http://localhost:5173").
                withSockJS()
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
        System.out.println("configureClientInboundChannel CALLED!!!!!");

        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                // Access STOMP headers from the incoming message
                StompHeaderAccessor accessor =
                        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

                if (accessor == null) return message;

                // Only authenticate on CONNECT
                if (StompCommand.CONNECT.equals(accessor.getCommand())) {

                    String authHeader = accessor.getFirstNativeHeader("Authorization");
                    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                        // No token -> reject connection (optional)
                        throw new IllegalArgumentException("Missing Authorization header");
                    }

                    String token = authHeader.substring(7);

                    // Validate token WITHOUT DB!!!
                    if (!jwtService.isTokenValid(token)) {  // implement: signature + expiration check
                        throw new IllegalArgumentException("Invalid JWT");
                    }

                    String username = jwtService.extractUsername(token);
                    if (username == null || username.isBlank()) {
                        throw new IllegalArgumentException("JWT has no subject");
                    }


                    Authentication auth =
                            new UsernamePasswordAuthenticationToken(
                                    username, null, jwtService.extractAuthorities(token));

                    accessor.setUser(auth);
                }

                return message;
            }
        });
    }
}
