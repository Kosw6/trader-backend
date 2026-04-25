package com.example.trader.service;

import com.example.trader.dto.map.ResponseEdgeDto;
import com.example.trader.dto.map.ResponseGraphDto;
import com.example.trader.dto.map.ResponseNodeDto;
import com.example.trader.dto.map.ResponseNodeSummaryDto;
import com.example.trader.repository.EdgeRepository;
import com.example.trader.repository.NodeRepository;
import com.example.trader.repository.NodeSummaryRepository;
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
public class GraphCacheService {

    private final NodeSummaryRepository nodeSummaryRepository;
    private final EdgeRepository edgeRepository;

    @Cacheable(
            cacheNames = "graphDetail",
            key = "#graphId",
            sync = true
    )
    @Transactional(readOnly = true)
    public ResponseGraphDto getCachedGraph(Long graphId) {
        log.info("CACHE MISS - DB 조회 실행 graphId={}", graphId);
        List<ResponseNodeSummaryDto> nodeDtos =
                nodeSummaryRepository.findAllFetchSummaryByPageId(graphId).stream()
                        .map(ResponseNodeSummaryDto::from)
                        .toList();

        List<ResponseEdgeDto> edgeDtos =
                edgeRepository.findAllByPageId(graphId).stream()
                        .map(ResponseEdgeDto::toResponseEdgeDto)
                        .toList();

        return new ResponseGraphDto(graphId, nodeDtos, edgeDtos);
    }

    @CacheEvict(cacheNames = "graphDetail", key = "#graphId")
    public void evictGraph(Long graphId) {
        log.info("CACHE EVICT graphId={}", graphId);
    }
}
