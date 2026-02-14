package com.example.WebChat;

import com.example.WebChat.DTO.CustomPrincipal;
import com.example.WebChat.Service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebSocketTest {

    @LocalServerPort
    private int port;

    @Autowired
    private JwtService jwtService;

    private WebSocketStompClient stompClient;

    @BeforeEach
    void setup() {
        this.stompClient = new WebSocketStompClient(new SockJsClient(
                List.of(new WebSocketTransport(new StandardWebSocketClient()))));
        this.stompClient.setMessageConverter(new MappingJackson2MessageConverter());
    }

    @Test
    @DisplayName("Should authenticate and connect to WebSocket with valid JWT")
    void shouldConnectWithValidToken() throws Exception {
        // 1. Προετοιμασία Token
        // Inside your test method
        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("userId", 1L);

// Create a dummy principal to satisfy the UserDetails requirement
        CustomPrincipal mockUser = new CustomPrincipal(
                1L, "Mitsaras", null, false, true, Collections.emptyList()
        );

// Now call it with the correct types
        String token = jwtService.generateToken(extraClaims, mockUser);

        // 2. Headers για το CONNECT frame
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + token);

        // 3. Απόπειρα σύνδεσης
        CompletableFuture<StompSession> completableFuture = new CompletableFuture<>();

        stompClient.connectAsync(
                "ws://localhost:" + port + "/ws",
                (WebSocketHttpHeaders) null, // Explicit cast solves the ambiguity
                connectHeaders,
                new StompSessionHandlerAdapter() {
                    @Override
                    public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                        completableFuture.complete(session);
                    }
                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {
                        // Εδώ μπορείς να πιάσεις errors αν αποτύχει το Auth
                    }
                }
        );

        // 4. Verification
        StompSession session = completableFuture.get(3, TimeUnit.SECONDS);

        assertThat(session).isNotNull();
        assertThat(session.isConnected()).isTrue();

        System.out.println("🚀 WebSocket connected and authenticated with ID 1!");
        session.disconnect();
    }
}