package com.example.trader.service;

import com.example.trader.dto.map.DirectoryWithPagesDto;
import com.example.trader.dto.map.InitPayload;
import com.example.trader.dto.map.ResponseDirectoryDto;
import com.example.trader.dto.map.ResponsePageDto;
import com.example.trader.repository.DirectoryRepository;
import com.example.trader.repository.PageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InitQuery {

    private final DirectoryRepository directoryRepository;
    private final PageRepository pageRepository;

    /** 유저 전체 초기 로딩: 디렉토리[] + 페이지[] 분리 제공 */
    public InitPayload loadAll(Long userId) {
        List<ResponseDirectoryDto> dirs = directoryRepository.findAllDtoByUserId(userId);
        List<ResponsePageDto> pages = pageRepository.findAllDtoByUserId(userId);
        return new InitPayload(dirs, pages);
    }

    /** 단일 디렉토리와 그 하위 페이지들 재조회 */
    public DirectoryWithPagesDto reloadDirectoryWithPages(Long dirId, Long userId) {
        ResponseDirectoryDto dir = directoryRepository.findDtoByIdAndUserId(dirId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Directory not found or no permission"));
        List<ResponsePageDto> pages = pageRepository.findAllDtoByDirectory(dirId, userId);
        return new DirectoryWithPagesDto(dir, pages);
    }
}
