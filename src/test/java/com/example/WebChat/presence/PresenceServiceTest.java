package com.example.WebChat.presence;

import com.example.WebChat.presence.PresenceService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PresenceServiceTest {

    @Mock StringRedisTemplate redisTemplate;
    @Mock SimpMessagingTemplate messagingTemplate;

    @Mock SetOperations<String, String> setOperations;
    @Mock ValueOperations<String, String> valueOperations;

    @InjectMocks PresenceService presenceService;

    @Captor ArgumentCaptor<Set<String>> presenceCaptor;

    private static final String TEST_USERNAME = "testuser";
    private static final String REDIS_SET_KEY = "chat:online_users";
    private static final String HEARTBEAT_PREFIX = "user:heartbeat:";

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForSet()).thenReturn(setOperations);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    /* =========================================================
       onConnect
       ========================================================= */

    @Nested
    @DisplayName("onConnect()")
    class OnConnectTests {

        @Test
        @DisplayName("Adds user + sets heartbeat + broadcasts presence")
        void shouldAddUserSetHeartbeatAndBroadcast() {
            // broadcastPresence needs members()
            when(setOperations.members(REDIS_SET_KEY)).thenReturn(Set.of(TEST_USERNAME));

            presenceService.onConnect(TEST_USERNAME);

            verify(setOperations).add(REDIS_SET_KEY, TEST_USERNAME);
            verify(valueOperations).set(
                    eq(HEARTBEAT_PREFIX + TEST_USERNAME),
                    eq("active"),
                    eq(Duration.ofMinutes(2))
            );
            verify(messagingTemplate).convertAndSend(
                    eq("/topic/public/presence"),
                    any(Set.class)
            );
        }

        @Test
        @DisplayName("Duplicate connect still updates heartbeat + broadcasts")
        void shouldHandleDuplicateConnection() {
            when(setOperations.members(REDIS_SET_KEY)).thenReturn(Set.of(TEST_USERNAME));

            presenceService.onConnect(TEST_USERNAME);

            verify(setOperations).add(REDIS_SET_KEY, TEST_USERNAME);
            verify(valueOperations).set(anyString(), eq("active"), eq(Duration.ofMinutes(2)));
            verify(messagingTemplate).convertAndSend(eq("/topic/public/presence"), any(Set.class));
        }
    }

    /* =========================================================
       onDisconnect
       ========================================================= */

    @Nested
    @DisplayName("onDisconnect()")
    class OnDisconnectTests {

        @Test
        @DisplayName("Removes user + deletes heartbeat + broadcasts")
        void shouldRemoveUserAndBroadcast() {
            when(setOperations.members(REDIS_SET_KEY)).thenReturn(Set.of()); // after removal

            presenceService.onDisconnect(TEST_USERNAME);

            verify(setOperations).remove(REDIS_SET_KEY, TEST_USERNAME);
            verify(redisTemplate).delete(HEARTBEAT_PREFIX + TEST_USERNAME);
            verify(messagingTemplate).convertAndSend(eq("/topic/public/presence"), any(Set.class));
        }

        @Test
        @DisplayName("Disconnect non-existent user still deletes heartbeat + broadcasts")
        void shouldHandleNonExistentUser() {
            when(setOperations.members(REDIS_SET_KEY)).thenReturn(Set.of()); // safe

            presenceService.onDisconnect(TEST_USERNAME);

            verify(setOperations).remove(REDIS_SET_KEY, TEST_USERNAME);
            verify(redisTemplate).delete(HEARTBEAT_PREFIX + TEST_USERNAME);
            verify(messagingTemplate).convertAndSend(eq("/topic/public/presence"), any(Set.class));
        }
    }

    /* =========================================================
       updateHeartbeat
       ========================================================= */

    @Nested
    @DisplayName("updateHeartbeat()")
    class UpdateHeartbeatTests {

        @Test
        @DisplayName("Sets heartbeat with 2 min TTL")
        void shouldUpdateHeartbeat() {
            presenceService.updateHeartbeat(TEST_USERNAME);

            verify(valueOperations).set(
                    eq(HEARTBEAT_PREFIX + TEST_USERNAME),
                    eq("active"),
                    eq(Duration.ofMinutes(2))
            );
        }
    }

    /* =========================================================
       broadcastPresence
       ========================================================= */

    @Nested
    @DisplayName("broadcastPresence()")
    class BroadcastPresenceTests {

        @Test
        @DisplayName("Broadcasts online users to topic")
        void shouldBroadcastOnlineUsers() {
            Set<String> onlineUsers = Set.of("user1", "user2", "user3");
            when(setOperations.members(REDIS_SET_KEY)).thenReturn(onlineUsers);

            presenceService.broadcastPresence();

            verify(messagingTemplate).convertAndSend(
                    eq("/topic/public/presence"),
                    presenceCaptor.capture()
            );

            assertThat(presenceCaptor.getValue())
                    .containsExactlyInAnyOrder("user1", "user2", "user3");
        }

        @Test
        @DisplayName("If Redis members() returns null -> no broadcast")
        void shouldNotBroadcastWhenMembersNull() {
            Set<String> onlineUsers = null;
            when(setOperations.members(REDIS_SET_KEY)).thenReturn(null);

            presenceService.broadcastPresence();

            verify(messagingTemplate, never()).convertAndSend("/topic/public/presence", onlineUsers);
        }
    }

    /* =========================================================
       getOnlineUsers
       ========================================================= */

    @Nested
    @DisplayName("getOnlineUsers()")
    class GetOnlineUsersTests {

        @Test
        @DisplayName("Returns set of online users")
        void shouldReturnOnlineUsers() {
            Set<String> expected = Set.of("user1", "user2");
            when(setOperations.members(REDIS_SET_KEY)).thenReturn(expected);

            Set<String> out = presenceService.getOnlineUsers();

            assertThat(out).isEqualTo(expected);
        }

        @Test
        @DisplayName("Returns null when Redis returns null")
        void shouldReturnNullWhenNoUsersOnline() {
            when(setOperations.members(REDIS_SET_KEY)).thenReturn(null);

            Set<String> out = presenceService.getOnlineUsers();

            assertThat(out).isNull();
        }
    }

    /* =========================================================
       performCleanup
       ========================================================= */

    @Nested
    @DisplayName("performCleanup()")
    class PerformCleanupTests {

        @Test
        @DisplayName("Removes users without heartbeat and broadcasts if changed")
        void shouldRemoveExpiredUsersAndBroadcast() {
            Set<String> onlineUsers = Set.of("user1", "user2", "user3");

            when(setOperations.members(REDIS_SET_KEY))
                    .thenReturn(onlineUsers)     // cleanup reads members()
                    .thenReturn(Set.of("user1")); // broadcastPresence reads members()

            when(redisTemplate.hasKey(HEARTBEAT_PREFIX + "user1")).thenReturn(true);
            when(redisTemplate.hasKey(HEARTBEAT_PREFIX + "user2")).thenReturn(false);
            when(redisTemplate.hasKey(HEARTBEAT_PREFIX + "user3")).thenReturn(false);

            presenceService.performCleanup();

            verify(setOperations).remove(REDIS_SET_KEY, "user2");
            verify(setOperations).remove(REDIS_SET_KEY, "user3");
            verify(setOperations, never()).remove(REDIS_SET_KEY, "user1");

            verify(messagingTemplate).convertAndSend(eq("/topic/public/presence"), any(Set.class));
        }

        @Test
        @DisplayName("No changes -> no broadcast")
        void shouldNotBroadcastIfNoChanges() {
            Set<String> onlineUsers = Set.of("user1", "user2", "user3");
            when(setOperations.members(REDIS_SET_KEY)).thenReturn(Set.of("user1", "user2"));

            when(redisTemplate.hasKey(HEARTBEAT_PREFIX + "user1")).thenReturn(true);
            when(redisTemplate.hasKey(HEARTBEAT_PREFIX + "user2")).thenReturn(true);

            presenceService.performCleanup();

            verify(setOperations, never()).remove(anyString(), anyString());
            verify(messagingTemplate, never()).convertAndSend("/topic/public/presence", onlineUsers);
        }

        @Test
        @DisplayName("Null onlineUsers -> return; no broadcast")
        void shouldHandleNullOnlineUsersSet() {
            Set<String> onlineUsers = null;
            when(setOperations.members(REDIS_SET_KEY)).thenReturn(null);

            presenceService.performCleanup();

            verify(setOperations, never()).remove(anyString(), anyString());
            verify(messagingTemplate, never()).convertAndSend("/topic/public/presence", onlineUsers);
        }

        @Test
        @DisplayName("Empty onlineUsers -> no removals, no broadcast")
        void shouldHandleEmptyOnlineUsersSet() {
            Set<String> onlineUsers = new HashSet<>();
            when(setOperations.members(REDIS_SET_KEY)).thenReturn(Set.of());

            presenceService.performCleanup();

            verify(setOperations, never()).remove(anyString(), anyString());
            verify(messagingTemplate, never()).convertAndSend("/topic/public/presence", onlineUsers);
        }
    }
}
