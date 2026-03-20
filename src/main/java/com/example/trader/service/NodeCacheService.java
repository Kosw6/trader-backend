package com.example.trader.service;

import com.example.trader.dto.map.ResponseNodeDto;
import com.example.trader.repository.NodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NodeCacheService {

    private final NodeRepository nodeRepository;

    @Cacheable(
            cacheNames = "pageNodes",
            key = "#pageId",
            sync = true
    )
    @Transactional(readOnly = true)
    public List<ResponseNodeDto> getCachedNodesByPageId(Long pageId) {
        log.info("CACHE MISS - DB 조회 실행 pageId={}", pageId);
        return nodeRepository.findAllFetchByPageId(pageId)
                .stream()
                .map(ResponseNodeDto::toResponseDtoToPreviewList)
                .toList();
    }

    @CacheEvict(cacheNames = "pageNodes", key = "#pageId")
    public void evictPageNodes(Long pageId) {
        log.info("CACHE EVICT pageId={}", pageId);
    }
}
