//package com.example.WebChat;
//
//import com.example.WebChat.conversation.dto.ChatListRow;
//import com.example.WebChat.conversation.dto.ConversationResponse;
//import com.example.WebChat.conversation.dto.GroupChatRequest;
//import com.example.WebChat.conversation.ConvMembership;
//import com.example.WebChat.conversation.Conversation;
//import com.example.WebChat.user.User;
//import com.example.WebChat.conversation.ConversationRepository;
//import com.example.WebChat.conversation.ConvMembershipRepository;
//import com.example.WebChat.user.UserRepository;
//import com.example.WebChat.conversation.ConversationService;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.DisplayName;
//import org.junit.jupiter.api.Nested;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.mockito.ArgumentCaptor;
//import org.mockito.Captor;
//import org.mockito.InjectMocks;
//import org.mockito.Mock;
//import org.mockito.junit.jupiter.MockitoExtension;
//import org.springframework.data.redis.core.HashOperations;
//import org.springframework.data.redis.core.StringRedisTemplate;
//
//import java.time.Instant;
//import java.util.*;
//
//import static org.assertj.core.api.Assertions.assertThat;
//import static org.assertj.core.api.Assertions.assertThatThrownBy;
//import static org.mockito.ArgumentMatchers.*;
//import static org.mockito.Mockito.*;
//
//@ExtendWith(MockitoExtension.class)
//class ConversationServiceTest {
//
//    @Mock
//    private StringRedisTemplate redisTemplate;
//
//    @Mock
//    private ConversationRepository conversationRepository;
//
//    @Mock
//    private ConvMembershipRepository convMembershipRepository;
//
//    @Mock
//    private UserRepository userRepository;
//
//    @Mock
//    private HashOperations<String, Object, Object> hashOperations;
//
//    @InjectMocks
//    private ConversationService conversationService;
//
//    @Captor
//    private ArgumentCaptor<Conversation> conversationCaptor;
//
//    @Captor
//    private ArgumentCaptor<List<ConvMembership>> membershipsCaptor;
//
//    private User user1;
//    private User user2;
//    private User user3;
//    private Conversation conversation;
//    private ConvMembership membership1;
//    private ConvMembership membership2;
//
//    @BeforeEach
//    void setUp() {
//        user1 = User.builder()
//                .id(1L)
//                .username("user1")
//                .email("user1@test.com")
//                .build();
//
//        user2 = User.builder()
//                .id(2L)
//                .username("user2")
//                .email("user2@test.com")
//                .build();
//
//        user3 = User.builder()
//                .id(3L)
//                .username("user3")
//                .email("user3@test.com")
//                .build();
//
//        conversation = Conversation.builder()
//                .conversationID(100L)
//                .conversationName("Test Group")
//                .isGroup(false)
//                .createdAt(Instant.now())
//                .build();
//
//        membership1 = ConvMembership.builder()
//                .id(1000L)
//                .user(user1)
//                .conversation(conversation)
//                .joinedAt(Instant.now())
//                .lastReadMessageId(10L)
//                .lastDeliveredMessageId(10L)
//                .muted(false)
//                .build();
//
//        membership2 = ConvMembership.builder()
//                .id(1001L)
//                .user(user2)
//                .conversation(conversation)
//                .joinedAt(Instant.now())
//                .lastReadMessageId(10L)
//                .lastDeliveredMessageId(10L)
//                .muted(false)
//                .build();
//    }
//
//    @Nested
//    @DisplayName("createDirectConversation Tests")
//    class CreateDirectConversationTests {
//
//        @Test
//        @DisplayName("Should create new direct conversation when none exists")
//        void shouldCreateNewDirectConversation() {
//            // Given
//            when(convMembershipRepository.findExistingDirectChatId(1L, 2L))
//                    .thenReturn(Optional.empty());
//
//            when(userRepository.findById(1L)).thenReturn(Optional.of(user1));
//            when(userRepository.findById(2L)).thenReturn(Optional.of(user2));
//
//            when(conversationRepository.save(any(Conversation.class)))
//                    .thenReturn(conversation);
//
//            when(convMembershipRepository.saveAll(anyList()))
//                    .thenReturn(List.of(membership1, membership2));
//
//            // When
//            Long result = conversationService.createDirectConversation(1L, 2L);
//
//            // Then
//            assertThat(result).isEqualTo(100L);
//
//            verify(conversationRepository).save(conversationCaptor.capture());
//            Conversation savedConversation = conversationCaptor.getValue();
//            assertThat(savedConversation.isGroup()).isFalse();
//            assertThat(savedConversation.getConversationName()).isNull();
//
//            verify(convMembershipRepository).saveAll(membershipsCaptor.capture());
//            List<ConvMembership> savedMemberships = membershipsCaptor.getValue();
//            assertThat(savedMemberships).hasSize(2);
//        }
//
//        @Test
//        @DisplayName("Should return existing conversation ID if direct chat already exists")
//        void shouldReturnExistingConversationId() {
//            // Given
//            when(convMembershipRepository.findExistingDirectChatId(1L, 2L))
//                    .thenReturn(Optional.of(100L));
//
//            // When
//            Long result = conversationService.createDirectConversation(1L, 2L);
//
//            // Then
//            assertThat(result).isEqualTo(100L);
//
//            verify(userRepository, never()).findById(any());
//            verify(conversationRepository, never()).save(any());
//            verify(convMembershipRepository, never()).saveAll(any());
//        }
//
//        @Test
//        @DisplayName("Should throw exception when creating chat with yourself")
//        void shouldThrowWhenCreatingChatWithYourself() {
//            assertThatThrownBy(() ->
//                    conversationService.createDirectConversation(1L, 1L))
//                    .isInstanceOf(IllegalArgumentException.class)
//                    .hasMessageContaining("You cannot start a conversation with yourself");
//        }
//
//        @Test
//        @DisplayName("Should throw exception when user not found")
//        void shouldThrowWhenUserNotFound() {
//            // Given
//            when(convMembershipRepository.findExistingDirectChatId(1L, 99L))
//                    .thenReturn(Optional.empty());
//            when(userRepository.findById(1L)).thenReturn(Optional.of(user1));
//            when(userRepository.findById(99L)).thenReturn(Optional.empty());
//
//            // When/Then
//            assertThatThrownBy(() ->
//                    conversationService.createDirectConversation(1L, 99L))
//                    .isInstanceOf(RuntimeException.class)
//                    .hasMessageContaining("User 2 not found");
//        }
//    }
//
////    @Nested
////    @DisplayName("openDirectChatPreview Tests")
////    class OpenDirectChatPreviewTests {
////
////        @Test
////        @DisplayName("Should return conversation preview for direct chat")
////        void shouldReturnDirectChatPreview() {
////            // Given
////            when(convMembershipRepository.findExistingDirectChatId(1L, 2L))
////                    .thenReturn(Optional.of(100L));
////            when(userRepository.findById(1L)).thenReturn(Optional.of(user1));
////            when(userRepository.findById(2L)).thenReturn(Optional.of(user2));
////
////            // When
////            ConversationResponse response = conversationService.openDirectChatPreview(1L, 2L);
////
////            // Then
////            assertThat(response.id()).isEqualTo(100L);
////            assertThat(response.name()).isEqualTo("user2"); // Shows other user's name
////            assertThat(response.unreadCount()).isZero();
////        }
////    }
//
//    @Nested
//    @DisplayName("createGroupChat Tests")
//    class CreateGroupChatTests {
//
//        @Test
//        @DisplayName("Should create new group chat")
//        void shouldCreateNewGroupChat() {
//            // Given
//            GroupChatRequest request = new GroupChatRequest(
//                    "Test Group",
//                    List.of(2L, 3L)
//            );
//
//            when(userRepository.getReferenceById(1L)).thenReturn(user1);
//            when(userRepository.findAllById(anyList()))
//                    .thenReturn(List.of(user1, user2, user3));
//
//            when(conversationRepository.save(any(Conversation.class)))
//                    .thenReturn(conversation);
//
//            when(convMembershipRepository.saveAll(anyList()))
//                    .thenReturn(List.of(membership1, membership2));
//
//            // When
//            Conversation result = conversationService.createGroupChat(request, 1L);
//
//            // Then
//            assertThat(result).isNotNull();
//            assertThat(result.getConversationName()).isEqualTo("Test Group");
//
//            verify(conversationRepository).save(conversationCaptor.capture());
//            Conversation savedConversation = conversationCaptor.getValue();
//            assertThat(savedConversation.isGroup()).isTrue();
//            assertThat(savedConversation.getConversationName()).isEqualTo("Test Group");
//
//            verify(convMembershipRepository).saveAll(membershipsCaptor.capture());
//            List<ConvMembership> savedMemberships = membershipsCaptor.getValue();
//            assertThat(savedMemberships).hasSize(3); // Creator + 2 members
//        }
//
//        @Test
//        @DisplayName("Should add creator to members if not already included")
//        void shouldAddCreatorToMembers() {
//            // Given
//            GroupChatRequest request = new GroupChatRequest(
//                    "Test Group",
//                    List.of(2L, 3L) // Creator (1L) not included
//            );
//
//            when(userRepository.getReferenceById(1L)).thenReturn(user1);
//            when(userRepository.findAllById(anyList()))
//                    .thenReturn(List.of(user1, user2, user3));
//
//            // When
//            conversationService.createGroupChat(request, 1L);
//
//            // Then
//            verify(userRepository).findAllById(argThat(ids -> {
//                // Convert Iterable to List to use containsAll
//                List<Long> idList = new ArrayList<>();
//                ids.forEach(idList::add);
//                return idList.containsAll(List.of(1L, 2L, 3L)) && idList.size() == 3;
//            }));
//        }
//    }
//
////    @Nested
////    @DisplayName("getUserChats Tests")
////    class GetUserChatsTests {
////
////        @Test
////        @DisplayName("Should return user chats with Redis enrichment")
////        void shouldReturnUserChatsWithRedisData() {
////            // Given
////            Long userId = 1L;
////
////            ChatListRow row1 = mock(ChatListRow.class);
////            when(row1.getConversationId()).thenReturn(100L);
////            when(row1.getDisplayName()).thenReturn("Chat 1");
////            when(row1.getUnreadCount()).thenReturn(2);
////
////            when(convMembershipRepository.findUserChats(userId))
////                    .thenReturn(List.of(row1));
////
////            when(userRepository.getReferenceById(userId)).thenReturn(user1);
////
////            // Mock Redis operations
////            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
////
////            Map<Object, Object> redisMeta = new HashMap<>();
////            redisMeta.put("lastContent", "New message from Redis");
////            redisMeta.put("lastMessageAt", Instant.now().toString());
////
////            when(hashOperations.entries("conv:meta:100")).thenReturn(redisMeta);
////
////            // When
////            List<ConversationResponse> responses = conversationService.getUserChats(userId);
////
////            // Then
////            assertThat(responses).hasSize(1);
////            ConversationResponse response = responses.getFirst();
////            assertThat(response.id()).isEqualTo(100L);
////            assertThat(response.name()).isEqualTo("Chat 1");
////            assertThat(response.lastMessage()).isEqualTo("New message from Redis"); // From Redis
////            assertThat(response.unreadCount()).isEqualTo(2); // From DB
////        }
////
////        @Test
////        @DisplayName("Should use DB data when Redis has no data")
////        void shouldUseDbDataWhenRedisEmpty() {
////            // Given
////            Long userId = 1L;
////            Instant lastMessageAt = Instant.now();
////
////            ChatListRow row1 = mock(ChatListRow.class);
////            when(row1.getConversationId()).thenReturn(100L);
////            when(row1.getDisplayName()).thenReturn("Chat 1");
////            when(row1.getLastContent()).thenReturn("DB message");
////            when(row1.getLastMessageAt()).thenReturn(lastMessageAt);
////            when(row1.getUnreadCount()).thenReturn(5);
////
////            when(convMembershipRepository.findUserChats(userId))
////                    .thenReturn(List.of(row1));
////
////            when(userRepository.getReferenceById(userId)).thenReturn(user1);
////
////            // Mock Redis - empty map
////            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
////            when(hashOperations.entries("conv:meta:100")).thenReturn(Map.of());
////
////            // When
////            List<ConversationResponse> responses = conversationService.getUserChats(userId);
////
////            // Then
////            assertThat(responses).hasSize(1);
////            ConversationResponse response = responses.getFirst();
////            assertThat(response.lastMessage()).isEqualTo("DB message");
////            assertThat(response.lastMessageAt()).isEqualTo(lastMessageAt);
////        }
////    }
//
//
//    @Nested
//    @DisplayName("toggleMute Tests")
//    class ToggleMuteTests {
//
//        @Test
//        @DisplayName("Should toggle mute status")
//        void shouldToggleMute() {
//            // Given
//            Long userId = 1L;
//            Long conversationId = 100L;
//            boolean status = true;
//
//            doNothing().when(convMembershipRepository).toggleMute(userId, conversationId, status);
//
//            // When
//            conversationService.toggleMute(userId, conversationId, status);
//
//            // Then
//            verify(convMembershipRepository).toggleMute(userId, conversationId, status);
//        }
//    }
//}