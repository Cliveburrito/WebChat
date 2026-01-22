package com.example.WebChat.UtilsConfigs;

import com.example.WebChat.Service.CustomUserDetailsService;
import com.example.WebChat.Service.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
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
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /** Service responsible for JWT generation, parsing and validation. */
    private final JwtService jwtService;

    /** Custom implementation of {@link org.springframework.security.core.userdetails.UserDetailsService} used to load user data. */
    private final CustomUserDetailsService userDetailsService;

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
     * @param config the {@link MessageBrokerRegistry} used to configure message routing
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Clients subscribe to destinations like /topic/room1
        config.enableSimpleBroker("/topic");

        // Clients send messages to /app/... which are handled by @MessageMapping methods
        config.setApplicationDestinationPrefixes("/app");
    }

    /**
     * Registers STOMP endpoints that clients use to establish WebSocket connections.
     *
     * <p>
     * The endpoint {@code /ws} is exposed, and SockJS fallback is enabled so that
     * clients without native WebSocket support can still connect.
     * </p>
     *
     * @param registry the registry to which STOMP endpoints are added
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Debug: verify that endpoint registration runs during startup
        System.out.println("registerStompEndpoints CALLED!");

        // WebSocket endpoint: ws://<host>/ws (with SockJS fallback)
        registry.addEndpoint("/ws").
                setAllowedOrigins("http://localhost:5173").
                withSockJS();
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
     *
     * @param registration the registration object used to add interceptors
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        System.out.println("configureClientInboundChannel CALLED!");

        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                // Access STOMP headers from the incoming message
                StompHeaderAccessor accessor =
                        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

                if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
                    // Extract "Authorization" header from the WebSocket CONNECT frame
                    String authHeader = accessor.getFirstNativeHeader("Authorization");
                    System.out.println("WS CONNECT Authorization header = " + authHeader);

                    // Expect header in form: "Authorization: Bearer <token>"
                    if (authHeader != null && authHeader.startsWith("Bearer ")) {
                        String token = authHeader.substring(7);
                        try {
                            // Extract username from JWT
                            String username = jwtService.extractUsername(token);
                            System.out.println("WS CONNECT username from token = " + username);

                            if (username != null) {
                                // Load user details from your UserDetailsService
                                UserDetails userDetails =
                                        userDetailsService.loadUserByUsername(username);

                                // Validate JWT against loaded user details
                                if (jwtService.isTokenValid(token, userDetails)) {
                                    // Create an Authentication object for the WebSocket session
                                    Authentication auth =
                                            new UsernamePasswordAuthenticationToken(
                                                    userDetails,
                                                    null,
                                                    userDetails.getAuthorities()
                                            );

                                    // Attach authentication principal to the WebSocket session
                                    accessor.setUser(auth);

                                    System.out.println("WS Principal set to " + username);
                                } else {
                                    System.out.println("WS token NOT valid");
                                }
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                            System.out.println("WS JWT error: " + e.getMessage());
                        }
                    } else {
                        System.out.println("WS CONNECT: no Authorization header");
                    }
                }

                // Return the (possibly modified) message so it can continue in the pipeline
                return message;
            }
        });
    }
}
