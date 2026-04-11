package com.example.WebChat.message;

import com.example.WebChat.conversation.Conversation;
import com.example.WebChat.conversation.MembershipGuard;
import com.example.WebChat.message.dto.ChatMessageResponse;
import com.example.WebChat.user.User;
import org.apache.fory.ThreadSafeFory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageQueryServiceTest {

    private static final Long USER_ID = 5L;
    private static final Long CONVERSATION_ID = 6L;

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private MembershipGuard membershipGuard;

    @Mock
    private MessageCacheService messageCacheService;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private RedisTemplate<String, byte[]> furyRedisTemplate;

    @Mock
    private ThreadSafeFory fury;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    @Mock
    private org.springframework.data.redis.core.HashOperations<String, Object, Object> furyHashOperations;

    @Mock
    private MessageReactionRepository messageReactionRepository;

    private MessageQueryService messageQueryService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        messageQueryService = new MessageQueryService(
                messageRepository,
                membershipGuard,
                messageCacheService,
                stringRedisTemplate,
                furyRedisTemplate,
                fury,
                messageReactionRepository
        );
    }

    @Test
    @DisplayName("getChatHistory returns cached messages for the hot window")
    void getChatHistory_returnsCachedMessages() {
        when(membershipGuard.isMember(USER_ID, CONVERSATION_ID)).thenReturn(true);
        when(messageCacheService.getIndexKey(CONVERSATION_ID)).thenReturn("chat:index:6");
        when(messageCacheService.getDataKey(CONVERSATION_ID)).thenReturn("chat:data:6");
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.reverseRange("chat:index:6", 0, 1)).thenReturn(new LinkedHashSet<>(List.of("11", "12")));
        doReturn(furyHashOperations).when(furyRedisTemplate).opsForHash();
        when(furyHashOperations.multiGet(eq("chat:data:6"), anyList())).thenReturn(List.of("a".getBytes(), "b".getBytes()));

        ChatMessageResponse first = new ChatMessageResponse(11L, "one", Instant.now(), "alice", CONVERSATION_ID, null, null, null, null, null, false, List.of(), List.of());
        ChatMessageResponse second = new ChatMessageResponse(12L, "two", Instant.now(), "bob", CONVERSATION_ID, null, null, null, null, null, false, List.of(), List.of());
        when(fury.deserialize("a".getBytes())).thenReturn(first);
        when(fury.deserialize("b".getBytes())).thenReturn(second);
        when(messageReactionRepository.summarizeForMessages(List.of(11L, 12L), USER_ID)).thenReturn(List.of());

        List<ChatMessageResponse> results = messageQueryService.getChatHistory(CONVERSATION_ID, 0, 2, USER_ID);

        assertThat(results).containsExactly(first, second);
        verifyNoInteractions(messageRepository);
        verify(messageCacheService, never()).refreshRedisCache(eq(CONVERSATION_ID), anyList());
    }

    @Test
    @DisplayName("getChatHistory warms the cache from Postgres on first-page miss")
    void getChatHistory_warmsCacheOnColdStart() {
        when(membershipGuard.isMember(USER_ID, CONVERSATION_ID)).thenReturn(true);
        when(messageCacheService.getIndexKey(CONVERSATION_ID)).thenReturn("chat:index:6");
        when(messageCacheService.getDataKey(CONVERSATION_ID)).thenReturn("chat:data:6");
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.reverseRange("chat:index:6", 0, 0)).thenReturn(Set.of());

        @SuppressWarnings("unchecked")
        Slice<Long> ids = org.mockito.Mockito.mock(Slice.class);
        when(ids.isEmpty()).thenReturn(false);
        when(ids.getContent()).thenReturn(List.of(20L, 10L));
        when(messageRepository.findMessageIds(
                CONVERSATION_ID,
                PageRequest.of(0, 100, Sort.by("sentAt").descending())
        )).thenReturn(ids);

        Message firstMessage = message(20L, "newer", "alice");
        Message secondMessage = message(10L, "older", "bob");
        when(messageRepository.findMessagesWithDetails(List.of(20L, 10L))).thenReturn(List.of(firstMessage, secondMessage));
        when(messageReactionRepository.summarizeForMessages(List.of(20L), USER_ID)).thenReturn(List.of());

        List<ChatMessageResponse> results = messageQueryService.getChatHistory(CONVERSATION_ID, 0, 1, USER_ID);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().id()).isEqualTo(20L);
        assertThat(results.getFirst().content()).isEqualTo("newer");

        ArgumentCaptor<List<ChatMessageResponse>> cacheCaptor = ArgumentCaptor.forClass(List.class);
        verify(messageCacheService).refreshRedisCache(eq(CONVERSATION_ID), cacheCaptor.capture());
        assertThat(cacheCaptor.getValue()).hasSize(2);
        assertThat(cacheCaptor.getValue()).extracting(ChatMessageResponse::id).containsExactly(20L, 10L);
    }

    @Test
    @DisplayName("searchInChat preserves ranked repository order")
    void searchInChat_preservesRankedOrder() {
        when(membershipGuard.isMember(USER_ID, CONVERSATION_ID)).thenReturn(true);
        when(messageRepository.searchMessageIds(CONVERSATION_ID, "hello")).thenReturn(List.of(20L, 10L));

        Message older = message(10L, "hello there", "bob");
        Message newer = message(20L, "hello world", "alice");
        when(messageRepository.findMessagesWithDetails(List.of(20L, 10L))).thenReturn(List.of(older, newer));
        when(messageReactionRepository.summarizeForMessages(List.of(20L, 10L), USER_ID)).thenReturn(List.of());

        List<ChatMessageResponse> results = messageQueryService.searchInChat(USER_ID, CONVERSATION_ID, "hello");

        assertThat(results).extracting(ChatMessageResponse::id).containsExactly(20L, 10L);
    }

    @Test
    @DisplayName("getChatHistoryBefore returns messages older than the cursor")
    void getChatHistoryBefore_returnsOlderMessages() {
        when(membershipGuard.isMember(USER_ID, CONVERSATION_ID)).thenReturn(true);

        @SuppressWarnings("unchecked")
        Slice<Long> ids = org.mockito.Mockito.mock(Slice.class);
        when(ids.getContent()).thenReturn(List.of(9L, 8L));
        when(messageRepository.findMessageIdsBefore(
                CONVERSATION_ID,
                10L,
                PageRequest.of(0, 2, Sort.by("id").descending())
        )).thenReturn(ids);

        Message firstMessage = message(9L, "older", "alice");
        Message secondMessage = message(8L, "oldest", "bob");
        when(messageRepository.findMessagesWithDetails(List.of(9L, 8L))).thenReturn(List.of(firstMessage, secondMessage));
        when(messageReactionRepository.summarizeForMessages(List.of(9L, 8L), USER_ID)).thenReturn(List.of());

        List<ChatMessageResponse> results = messageQueryService.getChatHistoryBefore(CONVERSATION_ID, 10L, 2, USER_ID);

        assertThat(results).extracting(ChatMessageResponse::id).containsExactly(9L, 8L);
    }

    @Test
    @DisplayName("searchInChat ignores blank queries")
    void searchInChat_ignoresBlankQuery() {
        when(membershipGuard.isMember(USER_ID, CONVERSATION_ID)).thenReturn(true);

        List<ChatMessageResponse> results = messageQueryService.searchInChat(USER_ID, CONVERSATION_ID, "   ");

        assertThat(results).isEmpty();
        verifyNoInteractions(messageRepository);
    }

    @Test
    @DisplayName("getChatHistory rejects non-members")
    void getChatHistory_rejectsNonMember() {
        when(membershipGuard.isMember(USER_ID, CONVERSATION_ID)).thenReturn(false);

        assertThatThrownBy(() -> messageQueryService.getChatHistory(CONVERSATION_ID, 0, 20, USER_ID))
                .isInstanceOf(AccessDeniedException.class);
    }

    private Message message(Long id, String content, String username) {
        User sender = User.builder()
                .id(id + 100)
                .username(username)
                .email(username + "@test.dev")
                .passwordHash("hash")
                .createdAt(Instant.now())
                .enabled(true)
                .stealthMode(false)
                .build();

        Conversation conversation = Conversation.builder()
                .id(CONVERSATION_ID)
                .conversationName("chat")
                .isGroup(false)
                .createdAt(Instant.now())
                .build();

        return Message.builder()
                .id(id)
                .message(content)
                .sentAt(Instant.now())
                .sender(sender)
                .conversation(conversation)
                .attachments(List.of())
                .build();
    }
}
