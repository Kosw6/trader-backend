package com.example.trader.service;

import com.example.trader.dto.map.ResponseGraphDto;
import com.example.trader.exception.BaseException;
import com.example.trader.httpresponse.BaseResponseStatus;
import com.example.trader.repository.PageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class GraphServiceTest {

    @Mock
    PageRepository pageRepository;

    @Mock
    GraphCacheService graphCacheService;

    @InjectMocks
    GraphService graphService;

    // ── getGraph (개인) ───────────────────────────

    @Test
    void 소유한_페이지ID로_조회하면_그래프_DTO가_반환된다() {
        // given
        Long pageId = 1L;
        Long userId = 10L;
        ResponseGraphDto expected = new ResponseGraphDto(pageId, List.of(), List.of());

        given(pageRepository.existsByIdAndUserId(pageId, userId)).willReturn(true);
        given(graphCacheService.getCachedGraph(pageId)).willReturn(expected);

        // when
        ResponseGraphDto result = graphService.getGraph(pageId, userId);

        // then
        assertThat(result).isEqualTo(expected);
        then(graphCacheService).should().getCachedGraph(pageId);
    }

    @Test
    void 소유하지_않은_페이지ID로_조회하면_예외가_발생한다() {
        Long pageId = 1L;
        Long userId = 10L;
        given(pageRepository.existsByIdAndUserId(pageId, userId)).willReturn(false);

        assertThatThrownBy(() ->
                graphService.getGraph(
                        pageId,userId
                )
        ).isInstanceOf(BaseException.class).satisfies(e -> assertThat(((BaseException) e).getStatus())
                .isEqualTo(BaseResponseStatus.ACCESS_DENIED));
    }

    // ── getTeamGraph ──────────────────────────────

    @Test
    void 팀_소속_그래프를_조회하면_DTO가_반환된다() {
        // given
        Long teamId = 1L;
        Long graphId = 10L;
        ResponseGraphDto expected = new ResponseGraphDto(graphId, List.of(), List.of());

        given(pageRepository.existsByIdAndDirectoryTeamId(graphId, teamId)).willReturn(true);
        given(graphCacheService.getCachedGraph(graphId)).willReturn(expected);

        // when
        ResponseGraphDto result = graphService.getTeamGraph(teamId, graphId);

        // then
        assertThat(result).isEqualTo(expected);
        then(graphCacheService).should().getCachedGraph(graphId);
    }

    @Test
    void 다른_팀의_그래프를_조회하면_FAIL_AUTHENTICATE_예외가_발생한다() {
        // given
        Long teamId = 1L;
        Long graphId = 10L;

        given(pageRepository.existsByIdAndDirectoryTeamId(graphId, teamId)).willReturn(false);

        // when & then
        assertThatThrownBy(() -> graphService.getTeamGraph(teamId, graphId))
                .isInstanceOf(BaseException.class)
                .satisfies(e -> assertThat(((BaseException) e).getStatus())
                        .isEqualTo(BaseResponseStatus.ACCESS_DENIED));

        then(graphCacheService).should(never()).getCachedGraph(any());
    }
}
