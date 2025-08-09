// DirectoryService.java
package com.example.trader.service;

import com.example.trader.dto.RequestDirectoryDto;
import com.example.trader.dto.ResponseDirectoryDto;
import com.example.trader.entity.Directory;
import com.example.trader.repository.DirectoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DirectoryService {

    private final DirectoryRepository directoryRepository;

    @Transactional
    public ResponseDirectoryDto createDirectory(RequestDirectoryDto dto) {
        Directory parent = null;
        if (dto.getParentId() != null) {
            parent = directoryRepository.findById(dto.getParentId())
                    .orElseThrow(() -> new IllegalArgumentException("Parent directory not found"));
        }

        Directory directory = Directory.builder()
                .name(dto.getName())
                .parent(parent)
                .build();

        Directory saved = directoryRepository.save(directory);
        return toResponseDto(saved);
    }

    @Transactional(readOnly = true)
    public ResponseDirectoryDto getDirectory(Long id) {
        Directory directory = directoryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Directory not found"));
        return toResponseDto(directory);
    }

    @Transactional
    public ResponseDirectoryDto updateDirectory(Long id, RequestDirectoryDto dto) {
        Directory directory = directoryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Directory not found"));

        Directory parent = null;
        if (dto.getParentId() != null) {
            parent = directoryRepository.findById(dto.getParentId())
                    .orElseThrow(() -> new IllegalArgumentException("Parent directory not found"));
        }

        directory = Directory.builder()
                .id(directory.getId())
                .name(dto.getName())
                .parent(parent)
                .children(directory.getChildren())
                .pages(directory.getPages())
                .build();

        Directory updated = directoryRepository.save(directory);
        return toResponseDto(updated);
    }

    @Transactional
    public void deleteDirectory(Long id) {
        directoryRepository.deleteById(id);
    }

    private ResponseDirectoryDto toResponseDto(Directory directory) {
        return ResponseDirectoryDto.builder()
                .id(directory.getId())
                .name(directory.getName())
                .parentId(directory.getParent() != null ? directory.getParent().getId() : null)
                .pages(directory.getPages())
                .build();
    }
}
