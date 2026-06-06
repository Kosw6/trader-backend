package com.example.trader.service;

import com.example.trader.dto.map.RequestEdgeDto;
import com.example.trader.dto.map.ResponseEdgeDto;
import com.example.trader.entity.*;
import com.example.trader.exception.BaseException;
import com.example.trader.httpresponse.BaseResponseStatus;
import com.example.trader.repository.EdgeRepository;
import com.example.trader.repository.NodeRepository;
import com.example.trader.repository.PageRepository;
import com.example.trader.support.fixtures.TestFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class EdgeServiceTest {

    @Mock EdgeRepository edgeRepository;
    @Mock PageRepository pageRepository;
    @Mock NodeRepository nodeRepository;
    @Mock GraphCacheService graphCacheService;

    @InjectMocks
    EdgeService edgeService;

    // ── 헬퍼 ──────────────────────────────────────

    private Page pageWithId(Long id) {
        User user = TestFixtures.userWithId();
        Directory dir = TestFixtures.dir(user);
        Page page = TestFixtures.page(user, dir);
        ReflectionTestUtils.setField(page, "id", id);
        return page;
    }

    private Node nodeWithId(Long id, Page page) {
        Node node = TestFixtures.node(page);
        ReflectionTestUtils.setField(node, "id", id);
        return node;
    }

    private Edge edgeWithId(Long id, Page page, Node source, Node target) {
        Edge edge = TestFixtures.edge(page, source, target);
        ReflectionTestUtils.setField(edge, "id", id);
        return edge;
    }

    private RequestEdgeDto edgeDto(Long sourceId, Long targetId) {
        RequestEdgeDto dto = new RequestEdgeDto();
        dto.setSourceId(sourceId);
        dto.setTargetId(targetId);
        dto.setType("default");
        return dto;
    }

    // ── createEdge (개인) ─────────────────────────

    @Test
    void 정상_요청이면_엣지가_저장되고_DTO가_반환된다() {
        // given
        Long pageId = 1L;
        Long userId = 10L;
        Page page = pageWithId(pageId);
        Node source = nodeWithId(100L, page);
        Node target = nodeWithId(200L, page);
        Edge saved = edgeWithId(999L, page, source, target);
        RequestEdgeDto dto = edgeDto(source.getId(), target.getId());

        given(pageRepository.existsByIdAndUserId(pageId, userId)).willReturn(true);
        given(nodeRepository.findById(source.getId())).willReturn(Optional.of(source));
        given(nodeRepository.findById(target.getId())).willReturn(Optional.of(target));
        given(edgeRepository.existsInPageBySourceTarget(pageId, source.getId(), target.getId())).willReturn(false);
        given(pageRepository.getReferenceById(pageId)).willReturn(page);
        given(edgeRepository.save(any(Edge.class))).willReturn(saved);

        // when
        ResponseEdgeDto result = edgeService.createEdge(dto, pageId, userId);

        // then
        assertThat(result.getId()).isEqualTo(999L);
        assertThat(result.getSourceId()).isEqualTo(source.getId());
        assertThat(result.getTargetId()).isEqualTo(target.getId());
        then(graphCacheService).should().evictGraph(pageId);
    }

    @Test
    void 소유하지_않은_페이지에_엣지를_생성하면_ACCESS_DENIED_예외가_발생한다() {
        // given
        Long pageId = 1L;
        Long userId = 10L;
        RequestEdgeDto dto = edgeDto(100L, 200L);

        given(pageRepository.existsByIdAndUserId(pageId, userId)).willReturn(false);

        // when & then
        assertThatThrownBy(() -> edgeService.createEdge(dto, pageId, userId))
                .isInstanceOf(BaseException.class)
                .satisfies(e -> assertThat(((BaseException) e).getStatus())
                        .isEqualTo(BaseResponseStatus.ACCESS_DENIED));

        then(nodeRepository).should(never()).findById(any());
        then(edgeRepository).should(never()).save(any());
    }

    @Test
    void source_노드가_다른_페이지_소속이면_ACCESS_DENIED_예외가_발생한다() {
        // given
        Long pageId = 1L;
        Long userId = 10L;
        Page page = pageWithId(pageId);
        Page otherPage = pageWithId(99L);

        Node source = nodeWithId(100L, otherPage); // 다른 페이지 소속
        Node target = nodeWithId(200L, page);
        RequestEdgeDto dto = edgeDto(source.getId(), target.getId());

        given(pageRepository.existsByIdAndUserId(pageId, userId)).willReturn(true);
        given(nodeRepository.findById(source.getId())).willReturn(Optional.of(source));
        given(nodeRepository.findById(target.getId())).willReturn(Optional.of(target));

        // when & then
        assertThatThrownBy(() -> edgeService.createEdge(dto, pageId, userId))
                .isInstanceOf(BaseException.class)
                .satisfies(e -> assertThat(((BaseException) e).getStatus())
                        .isEqualTo(BaseResponseStatus.ACCESS_DENIED));

        then(edgeRepository).should(never()).save(any());
    }

    @Test
    void target_노드가_다른_페이지_소속이면_ACCESS_DENIED_예외가_발생한다() {
        // given
        Long pageId = 1L;
        Long userId = 10L;
        Page page = pageWithId(pageId);
        Page otherPage = pageWithId(99L);

        Node source = nodeWithId(100L, page);
        Node target = nodeWithId(200L, otherPage); // 다른 페이지 소속
        RequestEdgeDto dto = edgeDto(source.getId(), target.getId());

        given(pageRepository.existsByIdAndUserId(pageId, userId)).willReturn(true);
        given(nodeRepository.findById(source.getId())).willReturn(Optional.of(source));
        given(nodeRepository.findById(target.getId())).willReturn(Optional.of(target));

        // when & then
        assertThatThrownBy(() -> edgeService.createEdge(dto, pageId, userId))
                .isInstanceOf(BaseException.class)
                .satisfies(e -> assertThat(((BaseException) e).getStatus())
                        .isEqualTo(BaseResponseStatus.ACCESS_DENIED));

        then(edgeRepository).should(never()).save(any());
    }

    @Test
    void 같은_source와_target_사이에_이미_엣지가_있으면_예외가_발생한다() {
        // given
        Long pageId = 1L;
        Long userId = 10L;
        Page page = pageWithId(pageId);
        Node source = nodeWithId(100L, page);
        Node target = nodeWithId(200L, page);
        RequestEdgeDto dto = edgeDto(source.getId(), target.getId());

        given(pageRepository.existsByIdAndUserId(pageId, userId)).willReturn(true);
        given(nodeRepository.findById(source.getId())).willReturn(Optional.of(source));
        given(nodeRepository.findById(target.getId())).willReturn(Optional.of(target));
        given(edgeRepository.existsInPageBySourceTarget(pageId, source.getId(), target.getId())).willReturn(true);

        // when & then
        assertThatThrownBy(() -> edgeService.createEdge(dto, pageId, userId))
                .isInstanceOf(IllegalArgumentException.class);

        then(edgeRepository).should(never()).save(any());
    }

    // ── updateEdge (개인) ─────────────────────────

    @Test
    void 정상_요청이면_기존_엣지를_삭제하고_새_엣지로_교체된다() {
        // given
        Long pageId = 1L;
        Long userId = 10L;
        Long edgeId = 50L;
        Page page = pageWithId(pageId);
        Node source = nodeWithId(100L, page);
        Node target = nodeWithId(200L, page);
        Edge oldEdge = edgeWithId(edgeId, page, source, target);
        Edge newEdge = edgeWithId(51L, page, source, target);
        RequestEdgeDto dto = edgeDto(source.getId(), target.getId());

        given(pageRepository.existsByIdAndUserId(pageId, userId)).willReturn(true);
        given(edgeRepository.findById(edgeId)).willReturn(Optional.of(oldEdge));
        given(nodeRepository.findById(source.getId())).willReturn(Optional.of(source));
        given(nodeRepository.findById(target.getId())).willReturn(Optional.of(target));
        given(edgeRepository.existsInPageBySourceTarget(pageId, source.getId(), target.getId())).willReturn(false);
        given(pageRepository.getReferenceById(pageId)).willReturn(page);
        given(edgeRepository.save(any(Edge.class))).willReturn(newEdge);

        // when
        ResponseEdgeDto result = edgeService.updateEdge(edgeId, dto, pageId, userId);

        // then
        assertThat(result.getId()).isEqualTo(51L);
        then(edgeRepository).should().delete(oldEdge);
        then(edgeRepository).should().flush();
        then(graphCacheService).should().evictGraph(pageId);
    }

    @Test
    void 소유하지_않은_페이지의_엣지를_수정하면_ACCESS_DENIED_예외가_발생한다() {
        // given
        Long pageId = 1L;
        Long userId = 10L;
        Long edgeId = 50L;
        RequestEdgeDto dto = edgeDto(100L, 200L);

        given(pageRepository.existsByIdAndUserId(pageId, userId)).willReturn(false);

        // when & then
        assertThatThrownBy(() -> edgeService.updateEdge(edgeId, dto, pageId, userId))
                .isInstanceOf(BaseException.class)
                .satisfies(e -> assertThat(((BaseException) e).getStatus())
                        .isEqualTo(BaseResponseStatus.ACCESS_DENIED));

        then(edgeRepository).should(never()).delete(any());
    }

    @Test
    void 자기_자신의_엣지는_중복_체크에서_제외되어_수정이_가능하다() {
        // given — 기존 엣지의 source/target과 dto의 source/target이 동일
        Long pageId = 1L;
        Long userId = 10L;
        Long edgeId = 50L;
        Page page = pageWithId(pageId);
        Node source = nodeWithId(100L, page);
        Node target = nodeWithId(200L, page);
        Edge oldEdge = edgeWithId(edgeId, page, source, target);
        Edge newEdge = edgeWithId(51L, page, source, target);
        RequestEdgeDto dto = edgeDto(source.getId(), target.getId()); // 동일한 source/target

        given(pageRepository.existsByIdAndUserId(pageId, userId)).willReturn(true);
        given(edgeRepository.findById(edgeId)).willReturn(Optional.of(oldEdge));
        given(nodeRepository.findById(source.getId())).willReturn(Optional.of(source));
        given(nodeRepository.findById(target.getId())).willReturn(Optional.of(target));
        given(edgeRepository.existsInPageBySourceTarget(pageId, source.getId(), target.getId())).willReturn(true); // 중복 존재하지만
        given(pageRepository.getReferenceById(pageId)).willReturn(page);
        given(edgeRepository.save(any(Edge.class))).willReturn(newEdge);

        // when & then — 자기 자신이므로 예외 없이 수정 가능
        ResponseEdgeDto result = edgeService.updateEdge(edgeId, dto, pageId, userId);

        assertThat(result.getId()).isEqualTo(51L);
        then(edgeRepository).should().delete(oldEdge);
    }

    // ── deleteEdge (개인) ─────────────────────────

    @Test
    void 정상_요청이면_엣지가_삭제되고_캐시가_무효화된다() {
        // given
        Long pageId = 1L;
        Long userId = 10L;
        Long edgeId = 50L;
        Page page = pageWithId(pageId);
        Node source = nodeWithId(100L, page);
        Node target = nodeWithId(200L, page);
        Edge edge = edgeWithId(edgeId, page, source, target);

        given(pageRepository.existsByIdAndUserId(pageId, userId)).willReturn(true);
        given(edgeRepository.findById(edgeId)).willReturn(Optional.of(edge));

        // when
        edgeService.deleteEdge(pageId, edgeId, userId);

        // then
        then(edgeRepository).should().delete(edge);
        then(graphCacheService).should().evictGraph(pageId);
    }

    @Test
    void 소유하지_않은_페이지의_엣지를_삭제하면_ACCESS_DENIED_예외가_발생한다() {
        // given
        Long pageId = 1L;
        Long userId = 10L;

        given(pageRepository.existsByIdAndUserId(pageId, userId)).willReturn(false);

        // when & then
        assertThatThrownBy(() -> edgeService.deleteEdge(pageId, 50L, userId))
                .isInstanceOf(BaseException.class)
                .satisfies(e -> assertThat(((BaseException) e).getStatus())
                        .isEqualTo(BaseResponseStatus.ACCESS_DENIED));

        then(edgeRepository).should(never()).delete(any());
        then(graphCacheService).should(never()).evictGraph(any());
    }

    @Test
    void 해당_페이지_소속이_아닌_엣지를_삭제하면_ACCESS_DENIED_예외가_발생한다() {
        // given
        Long pageId = 1L;
        Long userId = 10L;
        Long edgeId = 50L;
        Page otherPage = pageWithId(99L); // 다른 페이지 소속 엣지
        Node source = nodeWithId(100L, otherPage);
        Node target = nodeWithId(200L, otherPage);
        Edge edge = edgeWithId(edgeId, otherPage, source, target);

        given(pageRepository.existsByIdAndUserId(pageId, userId)).willReturn(true);
        given(edgeRepository.findById(edgeId)).willReturn(Optional.of(edge));

        // when & then
        assertThatThrownBy(() -> edgeService.deleteEdge(pageId, edgeId, userId))
                .isInstanceOf(BaseException.class)
                .satisfies(e -> assertThat(((BaseException) e).getStatus())
                        .isEqualTo(BaseResponseStatus.ACCESS_DENIED));

        then(edgeRepository).should(never()).delete(any());
    }

    // ── createTeamEdge ────────────────────────────

    @Test
    void 팀_그래프에_엣지를_생성하면_저장되고_캐시가_무효화된다() {
        // given
        Long teamId = 1L;
        Long graphId = 10L;
        Page page = pageWithId(graphId);
        Node source = nodeWithId(100L, page);
        Node target = nodeWithId(200L, page);
        Edge saved = edgeWithId(999L, page, source, target);
        RequestEdgeDto dto = edgeDto(source.getId(), target.getId());

        given(pageRepository.existsByIdAndDirectoryTeamId(graphId, teamId)).willReturn(true);
        given(nodeRepository.findTeamNode(source.getId(), graphId, teamId)).willReturn(Optional.of(source));
        given(nodeRepository.findTeamNode(target.getId(), graphId, teamId)).willReturn(Optional.of(target));
        given(edgeRepository.existsInPageBySourceTarget(graphId, source.getId(), target.getId())).willReturn(false);
        given(pageRepository.getReferenceById(graphId)).willReturn(page);
        given(edgeRepository.save(any(Edge.class))).willReturn(saved);

        // when
        ResponseEdgeDto result = edgeService.createTeamEdge(teamId, graphId, dto);

        // then
        assertThat(result.getId()).isEqualTo(999L);
        then(graphCacheService).should().evictGraph(graphId);
    }

    @Test
    void 팀_소속이_아닌_그래프에_엣지를_생성하면_ACCESS_DENIED_예외가_발생한다() {
        // given
        Long teamId = 1L;
        Long graphId = 10L;
        RequestEdgeDto dto = edgeDto(100L, 200L);

        given(pageRepository.existsByIdAndDirectoryTeamId(graphId, teamId)).willReturn(false);

        // when & then
        assertThatThrownBy(() -> edgeService.createTeamEdge(teamId, graphId, dto))
                .isInstanceOf(BaseException.class)
                .satisfies(e -> assertThat(((BaseException) e).getStatus())
                        .isEqualTo(BaseResponseStatus.ACCESS_DENIED));

        then(edgeRepository).should(never()).save(any());
    }

    @Test
    void 팀_그래프에_중복_엣지를_생성하면_예외가_발생한다() {
        // given
        Long teamId = 1L;
        Long graphId = 10L;
        Page page = pageWithId(graphId);
        Node source = nodeWithId(100L, page);
        Node target = nodeWithId(200L, page);
        RequestEdgeDto dto = edgeDto(source.getId(), target.getId());

        given(pageRepository.existsByIdAndDirectoryTeamId(graphId, teamId)).willReturn(true);
        given(nodeRepository.findTeamNode(source.getId(), graphId, teamId)).willReturn(Optional.of(source));
        given(nodeRepository.findTeamNode(target.getId(), graphId, teamId)).willReturn(Optional.of(target));
        given(edgeRepository.existsInPageBySourceTarget(graphId, source.getId(), target.getId())).willReturn(true);

        // when & then
        assertThatThrownBy(() -> edgeService.createTeamEdge(teamId, graphId, dto))
                .isInstanceOf(IllegalArgumentException.class);

        then(edgeRepository).should(never()).save(any());
    }

    // ── updateTeamEdge ────────────────────────────

    @Test
    void 팀_엣지를_수정하면_기존_엣지가_삭제되고_새_엣지로_교체된다() {
        // given
        Long teamId = 1L;
        Long graphId = 10L;
        Long edgeId = 50L;
        Page page = pageWithId(graphId);
        Node source = nodeWithId(100L, page);
        Node target = nodeWithId(200L, page);
        Edge oldEdge = edgeWithId(edgeId, page, source, target);
        Edge newEdge = edgeWithId(51L, page, source, target);
        RequestEdgeDto dto = edgeDto(source.getId(), target.getId());

        given(edgeRepository.findByIdInTeamGraph(edgeId, graphId, teamId)).willReturn(Optional.of(oldEdge));
        given(nodeRepository.findTeamNode(source.getId(), graphId, teamId)).willReturn(Optional.of(source));
        given(nodeRepository.findTeamNode(target.getId(), graphId, teamId)).willReturn(Optional.of(target));
        given(edgeRepository.existsInPageBySourceTarget(graphId, source.getId(), target.getId())).willReturn(false);
        given(pageRepository.getReferenceById(graphId)).willReturn(page);
        given(edgeRepository.save(any(Edge.class))).willReturn(newEdge);

        // when
        ResponseEdgeDto result = edgeService.updateTeamEdge(teamId, graphId, edgeId, dto);

        // then
        assertThat(result.getId()).isEqualTo(51L);
        then(edgeRepository).should().delete(oldEdge);
        then(edgeRepository).should().flush();
        then(graphCacheService).should().evictGraph(graphId);
    }

    @Test
    void 팀_소속이_아닌_엣지를_수정하면_ACCESS_DENIED_예외가_발생한다() {
        // given
        Long teamId = 1L;
        Long graphId = 10L;
        Long edgeId = 50L;
        RequestEdgeDto dto = edgeDto(100L, 200L);

        given(edgeRepository.findByIdInTeamGraph(edgeId, graphId, teamId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> edgeService.updateTeamEdge(teamId, graphId, edgeId, dto))
                .isInstanceOf(BaseException.class)
                .satisfies(e -> assertThat(((BaseException) e).getStatus())
                        .isEqualTo(BaseResponseStatus.ACCESS_DENIED));

        then(edgeRepository).should(never()).delete(any());
    }

    // ── deleteTeamEdge ────────────────────────────

    @Test
    void 팀_엣지를_삭제하면_엣지가_제거되고_캐시가_무효화된다() {
        // given
        Long teamId = 1L;
        Long graphId = 10L;
        Long edgeId = 50L;
        Page page = pageWithId(graphId);
        Node source = nodeWithId(100L, page);
        Node target = nodeWithId(200L, page);
        Edge edge = edgeWithId(edgeId, page, source, target);

        given(edgeRepository.findByIdInTeamGraph(edgeId, graphId, teamId)).willReturn(Optional.of(edge));

        // when
        edgeService.deleteTeamEdge(teamId, graphId, edgeId);

        // then
        then(edgeRepository).should().delete(edge);
        then(graphCacheService).should().evictGraph(graphId);
    }

    @Test
    void 팀_소속이_아닌_엣지를_삭제하면_ACCESS_DENIED_예외가_발생한다() {
        // given
        Long teamId = 1L;
        Long graphId = 10L;
        Long edgeId = 50L;

        given(edgeRepository.findByIdInTeamGraph(edgeId, graphId, teamId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> edgeService.deleteTeamEdge(teamId, graphId, edgeId))
                .isInstanceOf(BaseException.class)
                .satisfies(e -> assertThat(((BaseException) e).getStatus())
                        .isEqualTo(BaseResponseStatus.ACCESS_DENIED));

        then(edgeRepository).should(never()).delete(any());
        then(graphCacheService).should(never()).evictGraph(any());
    }
}
