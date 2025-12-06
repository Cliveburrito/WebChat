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
 * The WebSocketConfig.java file is a configuration class for setting up WebSocket messaging
 * and implements the WebSocketMessageBrokerConfigurer interface,
 * which provides methods to configure the message broker and register STOMP
 * (Simple Text Oriented Messaging Protocol) endpoints.
 */

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;


    /**The configureMessageBroker method configures the message broker,
     * which is responsible for routing messages from one client to another.
     *<p> </p>
     *config.enableSimpleBroker("/topic") enables a simple in-memory message broker
     *with a destination prefix /topic. This is where the server will send messages to clients.
     *<p> </p>
     *config.setApplicationDestinationPrefixes("/app") Sets the application destination prefix
     *to /app. This prefix is used to filter destinations targeted to
     *application-specific message-handling methods.
     */
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
    }

    /**
     * The registerStompEndpoints method registers the STOMP endpoints,
     * which clients will use to connect to the WebSocket server.
     * <p>
     * registry.addEndpoint("/ws").withSockJS() registers an endpoint at /ws and enables
     * SockJS fallback options. SockJS is a library that provides WebSocket-like communication
     * for browsers that don't support WebSocket.
     * @param registry ss
     */
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        System.out.println("✅ [DEBUG] registerStompEndpoints CALLED!"); // ← ΠΡΟΣΘΕΣΕ
        registry.addEndpoint("/ws").withSockJS();
    }



    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        System.out.println("✅ [DEBUG] configureClientInboundChannel CALLED!"); // ← ΠΡΟΣΘΕΣΕ ΑΥΤΟ
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor =
                        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    String authHeader = accessor.getFirstNativeHeader("Authorization");
                    System.out.println("WS CONNECT Authorization header = " + authHeader);

                    if (authHeader != null && authHeader.startsWith("Bearer ")) {
                        String token = authHeader.substring(7);
                        try {
                            String username = jwtService.extractUsername(token);
                            System.out.println("✅ WS CONNECT username from token = " + username);

                            if (username != null) {
                                UserDetails userDetails =
                                        userDetailsService.loadUserByUsername(username);

                                if (jwtService.isTokenValid(token, userDetails)) {
                                    Authentication auth =
                                            new UsernamePasswordAuthenticationToken(
                                                    userDetails,
                                                    null,
                                                    userDetails.getAuthorities()
                                            );
                                    accessor.setUser(auth);
                                    // ✅ ΚΡΙΣΙΜΟ: Όχι μόνο accessor.setUser, αλλά και simpUser header

                                    System.out.println("✅ WS Principal set to " + username);
                                } else {
                                    System.out.println("✅ WS token NOT valid");
                                }
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                            System.out.println("✅ WS JWT error: " + e.getMessage());
                        }
                    } else {
                        System.out.println("WS CONNECT: no Authorization header");
                    }
                }

                return message;
            }
        });
    }
}
