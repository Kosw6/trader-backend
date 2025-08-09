// PageController.java
package com.example.trader.controller;

import com.example.trader.dto.RequestPageDto;
import com.example.trader.dto.ResponsePageDto;
import com.example.trader.service.PageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/pages")
@RequiredArgsConstructor
public class PageController {

    private final PageService pageService;

    @PostMapping
    public ResponseEntity<ResponsePageDto> createPage(@RequestBody RequestPageDto dto) {
        return ResponseEntity.ok(pageService.createPage(dto));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ResponsePageDto> getPage(@PathVariable Long id) {
        return ResponseEntity.ok(pageService.getPage(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ResponsePageDto> updatePage(@PathVariable Long id, @RequestBody RequestPageDto dto) {
        return ResponseEntity.ok(pageService.updatePage(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePage(@PathVariable Long id) {
        pageService.deletePage(id);
        return ResponseEntity.noContent().build();
    }
}
