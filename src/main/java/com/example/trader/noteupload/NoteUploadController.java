package com.example.trader.noteupload;

import com.example.trader.security.service.SecurityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/note/uploads")
public class NoteUploadController {
    private final NoteUploadService service;
    public record Part(String content) {}
    @PostMapping public NoteUploadService.State start(@RequestBody NoteUploadService.Start request) {
        return service.start(SecurityService.getAuthenticationUserId(),request);
    }
    @GetMapping("/{id}") public NoteUploadService.State status(@PathVariable UUID id) {
        return service.status(SecurityService.getAuthenticationUserId(),id);
    }
    @PutMapping("/{id}/parts/{part}") public NoteUploadService.State part(@PathVariable UUID id,
            @PathVariable int part,@RequestBody Part request) {
        return service.part(SecurityService.getAuthenticationUserId(),id,part,request.content());
    }
    @PostMapping("/{id}/complete") public Long complete(@PathVariable UUID id) {
        return service.complete(SecurityService.getAuthenticationUserId(),id);
    }
    @DeleteMapping("/{id}") public ResponseEntity<Void> cancel(@PathVariable UUID id) {
        service.cancel(SecurityService.getAuthenticationUserId(),id);return ResponseEntity.noContent().build();
    }
    @ExceptionHandler(NoteUploadException.class) public ResponseEntity<Map<String,Object>> error(NoteUploadException error) {
        return ResponseEntity.status(error.status()).body(Map.of("status",error.status().value(),
            "errorCode",error.code(),"message",error.getMessage()));
    }
}
