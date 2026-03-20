package com.example.trader.service;

import com.example.trader.common.interceptor.TeamMemberRequired;
import com.example.trader.dto.map.ResponseEdgeDto;
import com.example.trader.dto.map.ResponseGraphDto;
import com.example.trader.dto.map.ResponseNodeDto;
import com.example.trader.entity.Edge;
import com.example.trader.entity.Node;
import com.example.trader.exception.BaseException;
import com.example.trader.httpresponse.BaseResponseStatus;
import com.example.trader.repository.EdgeRepository;
import com.example.trader.repository.NodeRepository;
import com.example.trader.repository.PageRepository;
import com.example.trader.security.service.SecurityService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GraphService {

    private final PageRepository pageRepository;
    private final GraphCacheService graphCacheService;

    @Transactional(readOnly = true)
    public ResponseGraphDto getGraph(Long pageId, Long userId) {
        if (!pageRepository.existsByIdAndUserId(pageId, userId)) {
            throw new IllegalArgumentException("Page not found or no permission");
        }

        return graphCacheService.getCachedGraph(pageId);
    }

    @Transactional(readOnly = true)
    public ResponseGraphDto getTeamGraph(Long teamId, Long graphId, Long userId) {
        if (!pageRepository.existsByIdAndDirectoryTeamId(graphId, teamId)) {
            throw new BaseException(BaseResponseStatus.FAIL_AUTHENTICATE);
        }

        return graphCacheService.getCachedGraph(graphId);
    }
}
