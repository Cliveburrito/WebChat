package com.example.WebChat.Controller;

import com.example.WebChat.DTO.*;
import com.example.WebChat.Service.ConversationService;
import com.example.WebChat.Service.MessageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/chats")
@RequiredArgsConstructor
@Slf4j
public class ConversationController {

    private final ConversationService conversationService;
    private final MessageService messageService;

    /**
     * Creates or retrieves a direct conversation between two users.
     */
    @PostMapping("/direct")
    public ResponseEntity<ConversationResponse> openConversation(@RequestBody OpenDirectChatRequest request) {
        ConversationResponse dto = conversationService.openDirectChatPreview(request.id1(), request.id2());
        return ResponseEntity.ok(dto);
    }


    @PostMapping("/group")
    public ResponseEntity<ConversationResponse> createGroup(
            @Valid @RequestBody OpenGroupChatRequest request,
            @AuthenticationPrincipal CustomPrincipal principal
    ) {
        ConversationResponse dto = conversationService.createGroupChatPreview(request, principal.id());
        return ResponseEntity.ok(dto);
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
        List<ChatMessageResponse> history = messageService.getChatHistory(conversationId, page, size, principal.id());
        log.info("User {} requests chat history for conversation with id: {}", principal.username() , conversationId);
        return ResponseEntity.ok(history);
    }
}