package com.example.WebChat;

import com.example.WebChat.DTO.OpenGroupChatRequest;
import com.example.WebChat.Entity.Conversation;
import com.example.WebChat.Entity.User;
import com.example.WebChat.Repository.ConvMembershipRepository;
import com.example.WebChat.Repository.ConversationRepository;
import com.example.WebChat.Repository.UserRepository;
import com.example.WebChat.Service.ConversationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.util.AssertionErrors.assertTrue;

@ExtendWith(MockitoExtension.class)
public class ConversationServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private ConversationRepository conversationRepository;
    @Mock private ConvMembershipRepository convMembershipRepository;

    @InjectMocks
    private ConversationService conversationService;

    @Test
    void createGroupChatSuccessfully() {
        // Arrange
        String creatorName = "Mitsos";
        OpenGroupChatRequest request = new OpenGroupChatRequest(List.of(2L, 3L), "Our Group");

        User creator = User.builder().id(1L).username(creatorName).build();
        User user2 = User.builder().id(2L).username("User2").build();
        User user3 = User.builder().id(3L).username("User3").build();

        // Mocking
        when(userRepository.findByUsername(creatorName)).thenReturn(Optional.of(creator));

        // Mocking
        when(userRepository.findAllById(anyList())).thenReturn(List.of(creator, user2, user3));

        // Mocking:
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(i -> i.getArguments()[0]);

        // Act
        Conversation result = conversationService.createGroupChat(request, creatorName);

        // Assert
        assertNotNull(result);
        assertEquals("Our Group", result.getConversationName());
        assertTrue("", result.isGroup());

        // Verify
        verify(conversationRepository, times(1)).save(any(Conversation.class));
        verify(convMembershipRepository, times(1)).saveAll(anyList());

        // Verify list is created and has 3 people
        verify(userRepository).findAllById(argThat(iterable -> {
            // Convert Iterable to List so that we can have .contains()
            List<Long> list = new ArrayList<>();
            iterable.forEach(list::add);

            return list.contains(1L) && list.size() == 3;
        }));
    }

    @Test
    void testMarkAsRead() {
        Long convId = 1L;
        String username = "Mitsos";

        conversationService.markAsRead(convId, username);

        // Verify the repository got called with the right parameters
        verify(convMembershipRepository, times(1)).resetUnreadCount(convId, username);
    }


}
