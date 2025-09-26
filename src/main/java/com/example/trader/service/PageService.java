// PageService.java
package com.example.trader.service;

import com.example.trader.dto.map.RequestPageDto;
import com.example.trader.dto.map.ResponsePageDto;
import com.example.trader.dto.map.UpdatePageReq;
import com.example.trader.entity.Directory;
import com.example.trader.entity.Page;
import com.example.trader.entity.User;
import com.example.trader.repository.DirectoryRepository;
import com.example.trader.repository.PageRepository;
import com.example.trader.security.service.SecurityService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PageService {

    private final PageRepository pageRepository;
    private final DirectoryRepository directoryRepository;
    private final SecurityService securityService;
    @Transactional
    public ResponsePageDto createPage(RequestPageDto dto,Long userId) {
        User user = securityService.getAuthenticationUser();
        Directory directory = directoryRepository.findById(dto.getDirectoryId())
                .orElseThrow(() -> new IllegalArgumentException("Directory not found"));

        Page page = Page.builder()
                .title(dto.getTitle())
                .user(user)
                .directory(directory)
                .build();

        Page saved = pageRepository.save(page);

        return toResponseDto(saved);
    }

    @Transactional(readOnly = true)
    public ResponsePageDto getPage(Long id,Long userId) {
        Page page = pageRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Page not found"));
        return toResponseDto(page);
    }
    @Transactional(readOnly = true)
    public List<ResponsePageDto> getPages(Long userId){
        return pageRepository.findAllByUserId(userId).stream().map(this::toResponseDto).collect(Collectors.toList());
    }
    @Transactional(readOnly = true)
    public List<Long> findIdsByDirectory(Long dirId, Long userId) {
        return pageRepository.findIdsByDirectory(dirId, userId);
    }


    /**
     * 페이지를 다른 디렉토리로 이동한다.
     *
     * @param pageId      이동할 페이지 ID
     * @param targetDirId 대상 디렉토리 ID
     * @param userId      로그인한 사용자 ID
     * @return 이동 전 원본 디렉토리 ID
     */
    @Transactional
    public Long move(Long pageId, Long targetDirId, Long userId) {
        // 1. 페이지 조회 + 소유권 검증
        Page page = pageRepository.findById(pageId)
                .orElseThrow(() -> new IllegalArgumentException("Page not found"));
        if (!page.getUser().getId().equals(userId)) {
            throw new SecurityException("No permission to move this page");
        }

        Long oldDirId = page.getDirectory().getId();

        // 2. 대상 디렉토리 조회 + 소유권 검증
        Directory targetDir = directoryRepository.findById(targetDirId)
                .orElseThrow(() -> new IllegalArgumentException("Target directory not found"));
        if (!targetDir.getUser().getId().equals(userId)) {
            throw new SecurityException("No permission to move into this directory");
        }

        // 3. 이동 처리
        page.setDir(targetDir);

        // JPA 영속 상태이므로 save 불필요 (변경 감지로 flush됨)
        return oldDirId;
    }
    @Transactional
    public Long updatePage(Long id, UpdatePageReq dto, Long userId){
        Page page = pageRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Page not found"));
        if (!page.getUser().getId().equals(userId)) {
            throw new SecurityException("No permission to move this page");
        }
        page.setTitle(dto.title());
        return page.getId();

    }

    @Transactional
    public void deletePage(Long id,Long userId) {
        pageRepository.deleteByIdAndUserId(id,userId);
    }

    private ResponsePageDto toResponseDto(Page page) {
        return ResponsePageDto.builder()
                .id(page.getId())
                .title(page.getTitle())
                .directoryId(page.getDirectory() != null ? page.getDirectory().getId() : null)
                .build();
    }
    //userId로 DB조회기에 보안절차 필요없음
    @Transactional(readOnly = true)
    public List<ResponsePageDto> getPagesByDirectory(Long dirId,Long userId){
        List<ResponsePageDto> allDtoByDirectory = pageRepository.findAllDtoByDirectory(dirId, userId);
        return allDtoByDirectory;
    }

    @Transactional(readOnly = true)
    public List<ResponsePageDto> getAllPagesByUserId(Long userId){
        List<ResponsePageDto> allDtoByUserId = pageRepository.findAllDtoByUserId(userId);
        return allDtoByUserId;
    }
}
