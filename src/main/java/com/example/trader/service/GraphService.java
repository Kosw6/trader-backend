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

@TeamMemberRequired
@Service
@RequiredArgsConstructor
public class GraphService {

    private final PageRepository pageRepository;
    private final NodeRepository nodeRepository;
    private final EdgeRepository edgeRepository;

    @Transactional(readOnly = true)
    public ResponseGraphDto getGraph(Long pageId, Long userId) {

        // 소유권/존재 검증(가벼운 쿼리)
        if (!pageRepository.existsByIdAndUserId(pageId, userId)) {
            throw new IllegalArgumentException("Page not found or no permission");
        }
        // 두 번의 간단한 쿼리로 안전하게 조회
        List<ResponseNodeDto> nodeDtos = nodeRepository.findAllFetchByPageId(pageId).stream().map(ResponseNodeDto::toResponseDtoToPreviewList)
                .collect(Collectors.toList());
        List<ResponseEdgeDto> edgeDtos = edgeRepository.findAllByPageId(pageId).stream().map(ResponseEdgeDto::toResponseEdgeDto)
                .collect(Collectors.toList());
        return new ResponseGraphDto(pageId, nodeDtos, edgeDtos);
    }

    @Transactional(readOnly = true)
    public ResponseGraphDto getTeamGraph(Long teamId, Long graphId, Long userId) {
        // 그래프(=페이지)가 그 팀 소속인지
        if (!pageRepository.existsByIdAndDirectoryTeamId(graphId, teamId)) {
            throw new BaseException(BaseResponseStatus.FAIL_AUTHENTICATE);
        }

        List<ResponseNodeDto> nodeDtos =
                nodeRepository.findAllFetchByPageId(graphId).stream()
                        .map(ResponseNodeDto::toResponseDtoToPreviewList)
                        .toList();

        List<ResponseEdgeDto> edgeDtos =
                edgeRepository.findAllByPageId(graphId).stream()
                        .map(ResponseEdgeDto::toResponseEdgeDto)
                        .toList();

        return new ResponseGraphDto(graphId, nodeDtos, edgeDtos);
    }
//    private ResponseNodeDto toResponseNodeDto(Node node) {
//        return ResponseNodeDto.toResponseDto(node);
//    }
}
