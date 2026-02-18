// DirectoryService.java
package com.example.trader.service;

import com.example.trader.dto.map.RequestDirectoryDto;
import com.example.trader.dto.map.ResponseDirectoryDto;
import com.example.trader.dto.map.UpdateDirReq;
import com.example.trader.entity.Directory;
import com.example.trader.entity.Team;
import com.example.trader.entity.User;
import com.example.trader.exception.BaseException;
import com.example.trader.httpresponse.BaseResponseStatus;
import com.example.trader.repository.DirectoryRepository;
import com.example.trader.repository.TeamRepository;
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
    private final TeamRepository teamRepository;


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

    @Transactional
    public ResponseDirectoryDto createTeamDirectory(Long teamId, RequestDirectoryDto dto) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new BaseException(BaseResponseStatus.INVALID_TEAM));

        Directory dir = Directory.builder()
                .name(dto.getName())
                .team(team)
                .user(null) // 팀 디렉토리
                .build();

        directoryRepository.save(dir);
        return ResponseDirectoryDto.ofDto(dir);
    }

    @Transactional(readOnly = true)
    public ResponseDirectoryDto getTeamDirectory(Long teamId, Long dirId) {
        Directory dir = directoryRepository.findById(dirId)
                .orElseThrow(() -> new BaseException(BaseResponseStatus.INVALID_DIRECTORY));

        if (dir.getTeam() == null || !dir.getTeam().getId().equals(teamId)) {
            throw new BaseException(BaseResponseStatus.FAIL_AUTHENTICATE);
        }

        return ResponseDirectoryDto.ofDto(dir);
    }

    @Transactional(readOnly = true)
    public List<ResponseDirectoryDto> getAllTeamDirectories(Long teamId) {
        return directoryRepository.findAllByTeamId(teamId)
                .stream()
                .map(ResponseDirectoryDto::ofDto)
                .toList();
    }

    @Transactional
    public ResponseDirectoryDto updateTeamDirectory(Long teamId, Long dirId, UpdateDirReq dto) {
        Directory dir = directoryRepository.findById(dirId)
                .orElseThrow(() -> new BaseException(BaseResponseStatus.INVALID_DIRECTORY));

        if (dir.getTeam() == null || !dir.getTeam().getId().equals(teamId)) {
            throw new BaseException(BaseResponseStatus.FAIL_AUTHENTICATE);
        }

        boolean exists = directoryRepository.existsByIdAndTeamId(dto.parentId(),teamId);
        if(!exists){
            new IllegalArgumentException("Parent directory not found");
        }
        Directory parent = null;
        if (dto.parentId() != null && dto.parentId() !=0L) {
            parent = directoryRepository.findById(dto.parentId())
                    .orElseThrow(() -> new IllegalArgumentException("Parent directory not found"));
        }

        dir.setParent(parent);
        dir.rename(dto.name()); // 너 기존 스타일대로
        return ResponseDirectoryDto.ofDto(dir);
    }

    @Transactional
    public void deleteTeamDirectory(Long teamId, Long dirId) {
        Directory dir = directoryRepository.findById(dirId)
                .orElseThrow(() -> new BaseException(BaseResponseStatus.INVALID_DIRECTORY));

        if (dir.getTeam() == null || !dir.getTeam().getId().equals(teamId)) {
            throw new BaseException(BaseResponseStatus.FAIL_AUTHENTICATE);
        }

        directoryRepository.delete(dir);
    }
}
