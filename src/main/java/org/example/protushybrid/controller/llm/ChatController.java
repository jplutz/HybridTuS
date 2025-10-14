package org.example.protushybrid.controller.llm;

import org.example.protushybrid.service.llm.ChatService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Chat endpoint supporting both general learning and practice-mode chat with exercise context.
 */
@RestController
@RequestMapping("/api")
public class ChatController {
    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/chat")
    public ResponseEntity<ChatService.ChatResponse> chat(@RequestBody ChatService.ChatRequest req) {
        ChatService.ChatResponse response = chatService.handleChat(req);
        return ResponseEntity.ok(response);
    }
}
