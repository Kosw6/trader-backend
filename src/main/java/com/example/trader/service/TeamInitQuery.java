package com.example.trader.service;

import com.example.trader.dto.map.DirectoryWithPagesDto;
import com.example.trader.dto.map.InitPayload;
import com.example.trader.dto.map.ResponseDirectoryDto;
import com.example.trader.dto.map.ResponsePageDto;
import com.example.trader.exception.BaseException;
import com.example.trader.httpresponse.BaseResponseStatus;
import com.example.trader.repository.DirectoryRepository;
import com.example.trader.repository.PageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeamInitQuery {

    private final DirectoryRepository directoryRepository;
    private final PageRepository pageRepository;

    /** 팀 초기 로딩: 디렉토리[] + 페이지[] */
    public InitPayload loadAll(Long teamId) {
        List<ResponseDirectoryDto> dirs = directoryRepository.findAllDtoByTeamId(teamId);
        List<ResponsePageDto> pages = pageRepository.findAllDtoByTeamId(teamId);
        return new InitPayload(dirs, pages);
    }

    /** 단일 디렉토리와 그 하위 페이지들 재조회 */
    public DirectoryWithPagesDto reloadDirectoryWithPages(Long teamId, Long dirId) {
        ResponseDirectoryDto dir = directoryRepository.findDtoByIdAndTeamId(dirId, teamId)
                .orElseThrow(() -> new BaseException(BaseResponseStatus.FAIL_AUTHENTICATE));

        List<ResponsePageDto> pages = pageRepository.findAllDtoByDirectoryAndTeamId(dirId, teamId);
        return new DirectoryWithPagesDto(dir, pages);
    }
}

