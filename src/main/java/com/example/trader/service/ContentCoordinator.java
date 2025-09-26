package com.example.trader.service;

import com.example.trader.dto.map.*;
import com.example.trader.repository.DirectoryRepository;
import com.example.trader.repository.PageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.crossstore.ChangeSetPersister;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ContentCoordinator {
    private final PageService pageService;           // 단일
    private final InitQuery initQuery;               // DTO 전용 read 컴포넌트
    private final DirectoryRepository directoryRepository;
    private final PageRepository pageRepository;

    @Transactional(readOnly = true)
    public InitPayload loadAll(Long userId) {
        return initQuery.loadAll(userId);
    }

//    /** 디렉토리 수정 후 해당 디렉토리와 페이지 목록 재전달 */
//    @Transactional
//    public DirectoryWithPagesDto updateDirectoryAndReload(Long dirId, UpdateDirReq req, Long userId) {
//        directoryService.updateDirectory(dirId, req, userId); // 권한/밸리데이션 포함
//        return initQuery.reloadDirectoryWithPages(dirId, userId);
//    }
    /**해당 디렉토리와 하위 재갱신*/
    @Transactional(readOnly = true)
    public DirectoryWithPagesDto loadDirectoryWithPages(Long dirId, Long userId){
        return initQuery.reloadDirectoryWithPages(dirId,userId);
    }

    /** 페이지를 다른 디렉토리로 이동 → 원본/대상 디렉토리 2개 재전달 */
    @Transactional
    public ChangeResponse movePageAndReload(Long pageId, Long targetDirId, Long userId) {
        Long oldDirId = pageService.move(pageId, targetDirId, userId); // 소유권/상태 검증 포함
        var affected = List.of(
                initQuery.reloadDirectoryWithPages(oldDirId, userId),
                initQuery.reloadDirectoryWithPages(targetDirId, userId)
        );
        return ChangeResponse.of(affected);
    }

    /** 디렉토리 삭제 전 페이지 ID 수집 → 삭제 → 삭제 목록 응답 */
    @Transactional
    public DeleteResponse deleteDirectoryWithPageIds(Long dirId, Long userId) {
        // 1) 삭제 전 ID 수집 (소유자 검증 포함)
        List<Long> dirIds = directoryRepository.findDirectorySubtreeIds(dirId, userId);
        if (dirIds.isEmpty()) throw new RuntimeException("Directory not found");

        List<Long> pageIds =  pageRepository.findPageIdsInDirectoryTree(dirId, userId);

        // 2) 실제 삭제 (루트만 삭제하면 DB CASCADE가 나머지 처리)
        directoryRepository.deleteById(dirId);

        // 3) 응답
        return DeleteResponse.of(dirIds, pageIds);
    }
}
