package com.example.WebChat.Controller;

import com.example.WebChat.DTO.OpenDirectChatRequest;
import com.example.WebChat.DTO.OpenGroupChatRequest;
import com.example.WebChat.Entity.Conversation;
import com.example.WebChat.Repository.ConversationRepository;
import com.example.WebChat.Service.ConversationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chats") // Base URL
@RequiredArgsConstructor      // Injects all 'final' fields automatically
public class ConversationController {

    // 1. Remove @Autowired and make final
    private final ConversationService conversationService;
    private final ConversationRepository conversationRepository;

    // (You don't need repositories here, the Service handles them!)

    // URL becomes: POST /api/chats/group
    @PostMapping("/group")
    public Conversation openGroupChat(@RequestBody OpenGroupChatRequest request) {
        // Ensure your DTO accessor is correct (request.userIds() vs request.userIDs())
        return conversationService.createGroupConversation(request.userIDs(), request.name());
    }

    // URL becomes: POST /api/chats/direct
    @PostMapping("/direct")
    public Conversation openConversation(@RequestBody OpenDirectChatRequest request) {
        return conversationService.createDirectConversation(request.id1(), request.id2());
    }

    @GetMapping("/getall")
    public List<Conversation> getAllConversations() {
        return conversationRepository.findAll();
    }
}