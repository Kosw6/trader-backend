package com.example.trader.service;

import com.example.trader.dto.map.*;
import com.example.trader.entity.Directory;
import com.example.trader.entity.Page;
import com.example.trader.exception.BaseException;
import com.example.trader.httpresponse.BaseResponseStatus;
import com.example.trader.repository.DirectoryRepository;
import com.example.trader.repository.PageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TeamContentCoordinator {

    private final PageService teamPageService;      // move 같은 팀 쓰기 로직
    private final TeamInitQuery teamInitQuery;
    private final DirectoryRepository directoryRepository;
    private final PageRepository pageRepository;

    @Transactional(readOnly = true)
    public InitPayload loadAll(Long teamId) {
        return teamInitQuery.loadAll(teamId);
    }

    @Transactional(readOnly = true)
    public DirectoryWithPagesDto loadDirectoryWithPages(Long teamId, Long dirId) {
        return teamInitQuery.reloadDirectoryWithPages(teamId, dirId);
    }

    @Transactional
    public ChangeResponse movePageAndReload(Long teamId, Long pageId, Long targetDirId) {
        Long oldDirId = teamPageService.move(teamId, pageId, targetDirId); // team 소속 검증 포함
        var affected = List.of(
                teamInitQuery.reloadDirectoryWithPages(teamId, oldDirId),
                teamInitQuery.reloadDirectoryWithPages(teamId, targetDirId)
        );
        return ChangeResponse.of(affected);
    }

    @Transactional
    public DeleteResponse deleteDirectoryWithPageIds(Long teamId, Long dirId) {

        // 팀 소속 검증 포함된 “서브트리 id” 쿼리로 가져오는 걸 추천
        List<Long> dirIds = directoryRepository.findDirectorySubtreeIdsInTeam(dirId, teamId);
        if (dirIds.isEmpty()) throw new BaseException(BaseResponseStatus.INVALID_DIRECTORY);

        List<Long> pageIds = pageRepository.findPageIdsInDirectoryTreeInTeam(dirId, teamId);

        directoryRepository.deleteById(dirId);

        return DeleteResponse.of(dirIds, pageIds);
    }
}

