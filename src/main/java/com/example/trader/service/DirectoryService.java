// DirectoryService.java
package com.example.trader.service;

import com.example.trader.dto.map.RequestDirectoryDto;
import com.example.trader.dto.map.ResponseDirectoryDto;
import com.example.trader.dto.map.UpdateDirReq;
import com.example.trader.entity.Directory;
import com.example.trader.entity.User;
import com.example.trader.repository.DirectoryRepository;
import com.example.trader.security.service.SecurityService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DirectoryService {

    private final DirectoryRepository directoryRepository;


    //유저 추가
    @Transactional
    public ResponseDirectoryDto createDirectory(RequestDirectoryDto dto, User user) {
        boolean exists = directoryRepository.existsByIdAndUserId(dto.getParentId(), user.getId());
        if(!exists){
            new IllegalArgumentException("Parent directory not found");
        }
        Directory parent = null;
        //널값이나 0이 아니면 부모도 조회 널이거나 0이면 부모없이 생성
        if (dto.getParentId() != null && dto.getParentId()!=0L) {
            parent = directoryRepository.findById(dto.getParentId())
                    .orElseThrow(() -> new IllegalArgumentException("Parent directory not found"));
        }

        Directory directory = Directory.builder()
                .user(user)
                .name(dto.getName())
                .parent(parent)
                .build();

        Directory saved = directoryRepository.save(directory);
        return toResponseDto(saved);
    }

    @Transactional(readOnly = true)
    public ResponseDirectoryDto getDirectory(Long id,Long userId) {
        Directory directory = directoryRepository.findByIdAndUserId(id,userId)
                .orElseThrow(() -> new IllegalArgumentException("Directory not found"));
        return toResponseDto(directory);
    }

    @Transactional(readOnly = true)
    public List<ResponseDirectoryDto> getAllDirectoryByUserId(Long userId){
        return directoryRepository.findAllByUserId(userId).stream().map(this::toResponseDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public ResponseDirectoryDto updateDirectory(Long id, UpdateDirReq dto,Long userId) {
        Directory directory = directoryRepository.findByIdAndUserId(id,userId)
                .orElseThrow(() -> new IllegalArgumentException("Directory not found"));
        boolean exists = directoryRepository.existsByIdAndUserId(dto.parentId(), userId);
        if(!exists){
            new IllegalArgumentException("Parent directory not found");
        }
        Directory parent = null;
        System.out.println(dto.parentId() == null);
        if (dto.parentId() != null && dto.parentId() !=0L) {
            parent = directoryRepository.findById(dto.parentId())
                    .orElseThrow(() -> new IllegalArgumentException("Parent directory not found"));
        }

        directory.setParent(parent);
        directory.rename(dto.name());
        return toResponseDto(directory);
    }

    @Transactional
    public void deleteDirectory(Long id,Long userId) {
        directoryRepository.deleteByIdAndUserId(id,userId);
    }

    private ResponseDirectoryDto toResponseDto(Directory directory) {
        return ResponseDirectoryDto.builder()
                .id(directory.getId())
                .name(directory.getName())
                .parentId(directory.getParent() != null ? directory.getParent().getId() : null)
                .build();
    }
}
