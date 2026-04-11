package com.example.WebChat.conversation;

import com.example.WebChat.auth.dto.CustomPrincipal;
import com.example.WebChat.conversation.dto.ConversationResponse;
import com.example.WebChat.conversation.dto.DirectChatRequest;
import com.example.WebChat.conversation.dto.GroupChatRequest;
import com.example.WebChat.message.MessageQueryService;
import com.example.WebChat.message.dto.ChatMessageResponse;
import com.example.WebChat.user.dto.UserResponse;
import com.example.WebChat.user.UserRepository;
import com.example.WebChat.shared.ResourceNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/chats")
@RequiredArgsConstructor
@Slf4j
public class ConversationController {

    private final ConversationService conversationService;
    private final MessageQueryService messageQueryService;
    private final ConversationCacheService conversationCacheService;
    private final SimpMessagingTemplate messagingTemplate;
    private final UserRepository userRepository;

    /**
     * Creates or retrieves a direct conversation between two users.
     */
    @PostMapping("/direct")
    public ResponseEntity<ConversationResponse> openConversation(
            @RequestBody DirectChatRequest request,
            @AuthenticationPrincipal CustomPrincipal principal
    ) {
        ConversationResponse dto = conversationService.openDirectChatPreview(principal.id(), request.id2());
        conversationCacheService.evictUserChats(List.of(principal.id(), request.id2()));
        publishConversationPreview(principal.username(), principal.id(), dto.conversationId());
        publishConversationPreviewForUserId(request.id2(), dto.conversationId());
        return ResponseEntity.ok(dto);
    }


    @PostMapping("/group")
    public ResponseEntity<ConversationResponse> createGroup(
            @Valid @RequestBody GroupChatRequest request,
            @AuthenticationPrincipal CustomPrincipal principal
    ) {
        ConversationResponse dto = conversationService.createGroupChatPreview(request, principal.id());
        java.util.LinkedHashSet<Long> affectedUserIds = new java.util.LinkedHashSet<>(request.memberIds());
        affectedUserIds.add(principal.id());
        conversationCacheService.evictUserChats(affectedUserIds);
        affectedUserIds.forEach(userId -> publishConversationPreviewForUserId(userId, dto.conversationId()));
        return ResponseEntity.ok(dto);
    }

    private void publishConversationPreview(String username, Long userId, Long conversationId) {
        ConversationResponse preview = conversationService.getConversationPreviewForUser(userId, conversationId);
        messagingTemplate.convertAndSendToUser(username, "/topic/conversations", preview);
    }

    private void publishConversationPreviewForUserId(Long userId, Long conversationId) {
        String username = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId))
                .getUsername();
        publishConversationPreview(username, userId, conversationId);
    }


    /**
     * Retrieves all conversations for the currently authenticated user.
     */
    @GetMapping("/my")
    public ResponseEntity<List<ConversationResponse>> getMyChats(@AuthenticationPrincipal CustomPrincipal principal) {
        return ResponseEntity.ok(conversationService.getUserChats(principal.id()));
    }


    @PatchMapping("/{id}/mute")
    public ResponseEntity<?> mute(@PathVariable Long id, @RequestParam boolean status, @AuthenticationPrincipal CustomPrincipal principal) {
        conversationService.toggleMute(principal.id(), id, status);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{conversationId}/messages")
    public ResponseEntity<List<ChatMessageResponse>> getChatHistory(
            @PathVariable Long conversationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @AuthenticationPrincipal CustomPrincipal principal) {

        // The service handles the SecurityContext internally, so we don't need Principal here
        List<ChatMessageResponse> history = messageQueryService.getChatHistory(conversationId, page, size, principal.id());
        log.info("User {} requests chat history for conversation with id: {}", principal.username() , conversationId);
        return ResponseEntity.ok(history);
    }

    @GetMapping("/{conversationId}/members")
    public ResponseEntity<List<UserResponse>> getChatMembers(
            @PathVariable Long conversationId,
            @AuthenticationPrincipal CustomPrincipal principal) {

        List<UserResponse> results = conversationService.getConversationMembers(conversationId, principal.getUserId());

        return ResponseEntity.ok(results);
    }
}
