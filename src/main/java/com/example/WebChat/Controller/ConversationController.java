package com.example.WebChat.Controller;

import com.example.WebChat.DTO.ChatMessageResponse;
import com.example.WebChat.DTO.ConversationResponse;
import com.example.WebChat.DTO.OpenDirectChatRequest;
import com.example.WebChat.DTO.OpenGroupChatRequest;
import com.example.WebChat.Entity.Conversation;
import com.example.WebChat.Service.ConversationService;
import com.example.WebChat.Service.MessageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
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
    public ResponseEntity<?> openConversation(@RequestBody OpenDirectChatRequest request) {
        Long conversationId = conversationService.createDirectConversation(request.id1(), request.id2());

        // We use a map for a lightweight response , so that we don't have to create a new DTO
        // so the frontend gets the JSON like key value-pair and not a single number!
        return ResponseEntity.ok(java.util.Map.of("conversationID", conversationId));
    }

    /**
     * Creates a new group conversation.
     */
    @PostMapping("/group")
    public ResponseEntity<?> createGroup(
            @Valid @RequestBody OpenGroupChatRequest request,
            Principal principal) {
        // We pass the creator's username and request
        Conversation conv = conversationService.createGroupChat(request, principal.getName());
        return ResponseEntity.ok(conv);
    }

    /**
     * Retrieves all conversations for the currently authenticated user.
     */
    @GetMapping("/my")
    public ResponseEntity<List<ConversationResponse>> getMyChats(Principal principal) {
        return ResponseEntity.ok(conversationService.getUserChats(principal.getName()));
    }


    /**
     * The endpoint the frontend uses to let the backend know the user has clicked
     * the chat and has read the messages!
     */
    @PostMapping("/{id}/read")
    public ResponseEntity<?> markAsRead(@PathVariable Long id, Principal principal) {
        conversationService.markAsRead(id, principal.getName());
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{id}/mute")
    public ResponseEntity<?> mute(@PathVariable Long id, @RequestParam boolean status, Principal principal) {
        conversationService.toggleMute(principal.getName(), id, status);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{conversationId}/messages")
    public ResponseEntity<List<ChatMessageResponse>> getChatHistory(
            @PathVariable Long conversationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Principal principal) {

        // The service handles the SecurityContext internally, so we don't need Principal here
        List<ChatMessageResponse> history = messageService.getChatHistory(conversationId, page, size);
        log.info("Use {} requests chat history for conversation with id: {}", principal.getName() , conversationId);
        return ResponseEntity.ok(history);
    }

}