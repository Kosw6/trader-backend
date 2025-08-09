// PageService.java
package com.example.trader.service;

import com.example.trader.dto.RequestPageDto;
import com.example.trader.dto.ResponsePageDto;
import com.example.trader.entity.Directory;
import com.example.trader.entity.Page;
import com.example.trader.repository.DirectoryRepository;
import com.example.trader.repository.PageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PageService {

    private final PageRepository pageRepository;
    private final DirectoryRepository directoryRepository;

    @Transactional
    public ResponsePageDto createPage(RequestPageDto dto) {
        Directory directory = directoryRepository.findById(dto.getDirectoryId())
                .orElseThrow(() -> new IllegalArgumentException("Directory not found"));

        Page page = Page.builder()
                .title(dto.getTitle())
                .content(dto.getContent())
                .directory(directory)
                .build();

        Page saved = pageRepository.save(page);

        return toResponseDto(saved);
    }

    @Transactional(readOnly = true)
    public ResponsePageDto getPage(Long id) {
        Page page = pageRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Page not found"));
        return toResponseDto(page);
    }

    @Transactional
    public ResponsePageDto updatePage(Long id, RequestPageDto dto) {
        Page page = pageRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Page not found"));

        page.getDirectory().getId(); // lazy loading

        page = Page.builder()
                .id(page.getId())
                .title(dto.getTitle())
                .content(dto.getContent())
                .directory(page.getDirectory())
                .nodeList(page.getNodeList())
                .edgeList(page.getEdgeList())
                .build();

        Page updated = pageRepository.save(page);
        return toResponseDto(updated);
    }

    @Transactional
    public void deletePage(Long id) {
        pageRepository.deleteById(id);
    }

    private ResponsePageDto toResponseDto(Page page) {
        return ResponsePageDto.builder()
                .id(page.getId())
                .title(page.getTitle())
                .content(page.getContent())
                .directoryId(page.getDirectory() != null ? page.getDirectory().getId() : null)
                .nodeList(page.getNodeList())
                .edgeList(page.getEdgeList())
                .build();
    }
}
