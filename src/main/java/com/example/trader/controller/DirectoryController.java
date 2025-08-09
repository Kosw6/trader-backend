// DirectoryController.java
package com.example.trader.controller;

import com.example.trader.dto.RequestDirectoryDto;
import com.example.trader.dto.ResponseDirectoryDto;
import com.example.trader.service.DirectoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/directories")
@RequiredArgsConstructor
public class DirectoryController {

    private final DirectoryService directoryService;

    @PostMapping
    public ResponseEntity<ResponseDirectoryDto> createDirectory(@RequestBody RequestDirectoryDto dto) {
        return ResponseEntity.ok(directoryService.createDirectory(dto));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ResponseDirectoryDto> getDirectory(@PathVariable Long id) {
        return ResponseEntity.ok(directoryService.getDirectory(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ResponseDirectoryDto> updateDirectory(@PathVariable Long id, @RequestBody RequestDirectoryDto dto) {
        return ResponseEntity.ok(directoryService.updateDirectory(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDirectory(@PathVariable Long id) {
        directoryService.deleteDirectory(id);
        return ResponseEntity.noContent().build();
    }
}
