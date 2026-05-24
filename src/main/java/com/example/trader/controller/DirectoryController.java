// DirectoryController.java
package com.example.trader.controller;

import com.example.trader.dto.map.RequestDirectoryDto;
import com.example.trader.dto.map.ResponseDirectoryDto;
import com.example.trader.entity.User;
import com.example.trader.security.details.UserContext;
import com.example.trader.security.service.SecurityService;
import com.example.trader.service.DirectoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/directories")
@RequiredArgsConstructor
public class DirectoryController {

    private final DirectoryService directoryService;
    private final SecurityService securityService;
    /** 디렉토리 생성(dto) */
    @PostMapping
    public ResponseEntity<ResponseDirectoryDto> createDirectory(@RequestBody RequestDirectoryDto dto) {
        User user = securityService.getAuthenticationUser();
        return ResponseEntity.ok(directoryService.createDirectory(dto,user));
    }

    /** 개별 디렉토리 조회(dto) */
    @GetMapping("/{id}")
    public ResponseEntity<ResponseDirectoryDto> getDirectory(@PathVariable Long id,@AuthenticationPrincipal UserContext user) {
        return ResponseEntity.ok(directoryService.getDirectory(id,user.getUserDto().getId()));
    }
    /** 유저의 전체 디렉토리 목록 (DTO) */
    @GetMapping
    public List<ResponseDirectoryDto> list(@AuthenticationPrincipal UserContext user) {
        return directoryService.getAllDirectoryByUserId(user.getUserDto().getId());
    }

}

