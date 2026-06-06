package com.example.pfe.Controller;

import com.example.pfe.Service.ChatRouterService;
import com.example.pfe.Service.RagService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
public class ChatController {

    private final RagService ragService;
    private final ChatRouterService chatRouter;  // ← added

    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> uploadPdf(
            @RequestParam("file") MultipartFile file) throws IOException {

        ragService.ingestPdf(file);
        return ResponseEntity.ok(Map.of(
                "message", "PDF uploaded and indexed successfully",
                "filename", file.getOriginalFilename()
        ));
    }

    @PostMapping("/message")
    public ResponseEntity<Map<String, String>> chat(
            @RequestBody Map<String, String> request) {

        String question = request.get("question");
        String response = chatRouter.route(question);  // ← now goes through router
        return ResponseEntity.ok(Map.of("response", response));
    }
}