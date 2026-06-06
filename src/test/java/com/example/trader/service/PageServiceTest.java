package com.example.trader.service;

import com.example.trader.dto.map.RequestPageDto;
import com.example.trader.dto.map.ResponsePageDto;
import com.example.trader.dto.map.UpdatePageReq;
import com.example.trader.entity.*;
import com.example.trader.exception.BaseException;
import com.example.trader.httpresponse.BaseResponseStatus;
import com.example.trader.repository.DirectoryRepository;
import com.example.trader.repository.PageRepository;
import com.example.trader.repository.UserTeamRepository;
import com.example.trader.security.service.SecurityService;
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
class PageServiceTest {

    @Mock PageRepository pageRepository;
    @Mock DirectoryRepository directoryRepository;
    @Mock SecurityService securityService;
    @Mock UserTeamRepository userTeamRepository;

    @InjectMocks
    PageService pageService;

    // ── 헬퍼 ──────────────────────────────────────

    private Directory dirWithId(Long id, User user) {
        Directory dir = TestFixtures.dir(user);
        ReflectionTestUtils.setField(dir, "id", id);
        return dir;
    }

    private Directory teamDirWithId(Long id, Team team) {
        Directory dir = Directory.builder().name("teamDir").team(team).build();
        ReflectionTestUtils.setField(dir, "id", id);
        return dir;
    }

    private Page pageWithId(Long id, User user, Directory dir) {
        Page page = TestFixtures.page(user, dir);
        ReflectionTestUtils.setField(page, "id", id);
        return page;
    }

    private RequestPageDto reqDto(String title, Long dirId) {
        RequestPageDto dto = new RequestPageDto();
        dto.setTitle(title);
        dto.setDirectoryId(dirId);
        return dto;
    }

    // ── createPage (개인) ─────────────────────────

    @Test
    void 정상_요청이면_페이지가_저장되고_DTO가_반환된다() {
        // given
        User user = TestFixtures.userWithId();
        Directory dir = dirWithId(10L, user);
        Page saved = pageWithId(1L, user, dir);
        RequestPageDto dto = reqDto("myPage", dir.getId());

        given(securityService.getAuthenticationUser()).willReturn(user);
        given(directoryRepository.findById(dir.getId())).willReturn(Optional.of(dir));
        given(pageRepository.save(any(Page.class))).willReturn(saved);

        // when
        ResponsePageDto result = pageService.createPage(dto, user.getId());

        // then
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getTitle()).isEqualTo(saved.getTitle());
        assertThat(result.getDirectoryId()).isEqualTo(dir.getId());
        then(pageRepository).should().save(any(Page.class));
    }

    // ── createTeamPage ────────────────────────────

    @Test
    void 팀_소속_디렉토리에_페이지를_생성하면_저장된다() {
        // given
        User user = TestFixtures.userWithId();
        Team team = TestFixtures.teamWithId();
        Directory dir = teamDirWithId(10L, team);
        Page saved = pageWithId(1L, user, dir);
        RequestPageDto dto = reqDto("teamPage", dir.getId());

        given(directoryRepository.findByIdAndTeamId(dir.getId(), team.getId())).willReturn(Optional.of(dir));
        given(securityService.getAuthenticationUser()).willReturn(user);
        given(pageRepository.save(any(Page.class))).willReturn(saved);

        // when
        Page result = pageService.createTeamPage(team.getId(), dto, user.getId());

        // then
        assertThat(result).isNotNull();
        then(pageRepository).should().save(any(Page.class));
    }

    @Test
    void 팀_소속이_아닌_디렉토리에_생성하면_예외가_발생한다() {
        // given
        User user = TestFixtures.userWithId();
        Team team = TestFixtures.teamWithId();
        Long wrongDirId = 99L;
        RequestPageDto dto = reqDto("teamPage", wrongDirId);

        given(directoryRepository.findByIdAndTeamId(wrongDirId, team.getId())).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> pageService.createTeamPage(team.getId(), dto, user.getId()))
                .isInstanceOf(BaseException.class)
                .satisfies(e -> assertThat(((BaseException) e).getStatus())
                        .isEqualTo(BaseResponseStatus.FAIL_AUTHENTICATE));

        then(pageRepository).should(never()).save(any());
    }

    // ── move (개인) ───────────────────────────────

    @Test
    void 소유한_페이지를_이동하면_이전_디렉토리ID가_반환된다() {
        // given
        User user = TestFixtures.userWithId();
        Directory oldDir = dirWithId(10L, user);
        Directory targetDir = dirWithId(20L, user);
        Page page = pageWithId(1L, user, oldDir);

        given(pageRepository.findById(page.getId())).willReturn(Optional.of(page));
        given(directoryRepository.findById(targetDir.getId())).willReturn(Optional.of(targetDir));

        // when
        Long oldDirId = pageService.move(page.getId(), targetDir.getId(), user.getId());

        // then
        assertThat(oldDirId).isEqualTo(oldDir.getId());
        assertThat(page.getDirectory()).isEqualTo(targetDir); // 실제로 이동됐는지
    }

    @Test
    void 다른_유저의_페이지를_이동하면_SecurityException이_발생한다() {
        // given
        User owner = TestFixtures.userWithId();
        User other = TestFixtures.userWithId();
        Directory dir = dirWithId(10L, owner);
        Page page = pageWithId(1L, owner, dir);

        given(pageRepository.findById(page.getId())).willReturn(Optional.of(page));

        // when & then
        assertThatThrownBy(() -> pageService.move(page.getId(), 20L, other.getId()))
                .isInstanceOf(SecurityException.class);

        then(directoryRepository).should(never()).findById(any());
    }

    @Test
    void 다른_유저의_디렉토리로_이동하면_SecurityException이_발생한다() {
        // given
        User user = TestFixtures.userWithId();
        User other = TestFixtures.userWithId();
        Directory ownDir = dirWithId(10L, user);
        Directory otherDir = dirWithId(20L, other); // 다른 유저 소유
        Page page = pageWithId(1L, user, ownDir);

        given(pageRepository.findById(page.getId())).willReturn(Optional.of(page));
        given(directoryRepository.findById(otherDir.getId())).willReturn(Optional.of(otherDir));

        // when & then
        assertThatThrownBy(() -> pageService.move(page.getId(), otherDir.getId(), user.getId()))
                .isInstanceOf(SecurityException.class);
    }

    // ── teamPageMove ──────────────────────────────

    @Test
    void 팀_소속_페이지를_이동하면_이전_디렉토리ID가_반환된다() {
        // given
        User user = TestFixtures.userWithId();
        Team team = TestFixtures.teamWithId();
        Directory oldDir = teamDirWithId(10L, team);
        Directory targetDir = teamDirWithId(20L, team);
        Page page = pageWithId(1L, user, oldDir);

        given(pageRepository.findByIdAndDirectoryTeamId(page.getId(), team.getId())).willReturn(Optional.of(page));
        given(directoryRepository.findByIdAndTeamId(targetDir.getId(), team.getId())).willReturn(Optional.of(targetDir));

        // when
        Long oldDirId = pageService.teamPageMove(team.getId(), page.getId(), targetDir.getId());

        // then
        assertThat(oldDirId).isEqualTo(oldDir.getId());
        assertThat(page.getDirectory()).isEqualTo(targetDir);
    }

    @Test
    void 팀_소속이_아닌_페이지를_이동하면_예외가_발생한다() {
        // given
        Team team = TestFixtures.teamWithId();

        given(pageRepository.findByIdAndDirectoryTeamId(99L, team.getId())).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> pageService.teamPageMove(team.getId(), 99L, 20L))
                .isInstanceOf(BaseException.class)
                .satisfies(e -> assertThat(((BaseException) e).getStatus())
                        .isEqualTo(BaseResponseStatus.FAIL_AUTHENTICATE));

        then(directoryRepository).should(never()).findByIdAndTeamId(any(), any());
    }

    // ── updatePage (개인) ─────────────────────────

    @Test
    void 소유한_페이지의_제목을_변경하면_정상_반영된다() {
        // given
        User user = TestFixtures.userWithId();
        Directory dir = dirWithId(10L, user);
        Page page = pageWithId(1L, user, dir);
        UpdatePageReq dto = new UpdatePageReq("newTitle");

        given(pageRepository.findById(page.getId())).willReturn(Optional.of(page));

        // when
        pageService.updatePage(page.getId(), dto, user.getId());

        // then
        assertThat(page.getTitle()).isEqualTo("newTitle");
    }

    @Test
    void 다른_유저의_페이지를_수정하면_SecurityException이_발생한다() {
        // given
        User owner = TestFixtures.userWithId();
        User other = TestFixtures.userWithId();
        Directory dir = dirWithId(10L, owner);
        Page page = pageWithId(1L, owner, dir);
        UpdatePageReq dto = new UpdatePageReq("newTitle");

        given(pageRepository.findById(page.getId())).willReturn(Optional.of(page));

        // when & then
        assertThatThrownBy(() -> pageService.updatePage(page.getId(), dto, other.getId()))
                .isInstanceOf(SecurityException.class);
    }

    // ── deletePage (개인) ─────────────────────────

    @Test
    void 소유한_페이지를_삭제하면_repository의_delete가_호출된다() {
        // given
        User user = TestFixtures.userWithId();
        Directory dir = dirWithId(10L, user);
        Page page = pageWithId(1L, user, dir);

        given(pageRepository.findByIdAndUserId(page.getId(), user.getId())).willReturn(Optional.of(page));

        // when
        pageService.deletePage(page.getId(), user.getId());

        // then
        then(pageRepository).should().delete(page);
    }

    @Test
    void 다른_유저의_페이지를_삭제하면_FAIL_AUTHENTICATE_예외가_발생한다() {
        // given
        Long pageId = 1L;
        Long otherUserId = 99L;

        given(pageRepository.findByIdAndUserId(pageId, otherUserId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> pageService.deletePage(pageId, otherUserId))
                .isInstanceOf(BaseException.class)
                .satisfies(e -> assertThat(((BaseException) e).getStatus())
                        .isEqualTo(BaseResponseStatus.FAIL_AUTHENTICATE));

        then(pageRepository).should(never()).delete(any());
    }

    // ── deleteTeamPage ────────────────────────────

    @Test
    void OWNER_권한이면_팀_페이지를_삭제할_수_있다() {
        // given
        User owner = TestFixtures.userWithId();
        Team team = TestFixtures.teamWithId();
        Directory dir = teamDirWithId(10L, team);
        Page page = pageWithId(1L, owner, dir);

        given(userTeamRepository.existsByTeamIdAndUserIdAndRole(team.getId(), owner.getId(), TeamRole.OWNER))
                .willReturn(true);
        given(pageRepository.findByIdAndDirectoryTeamId(page.getId(), team.getId()))
                .willReturn(Optional.of(page));

        // when
        pageService.deleteTeamPage(team.getId(), page.getId(), owner.getId());

        // then
        then(pageRepository).should().delete(page);
    }

    @Test
    void OWNER가_아니면_팀_페이지_삭제시_FAIL_AUTHENTICATE_예외가_발생한다() {
        // given
        User notOwner = TestFixtures.userWithId();
        Team team = TestFixtures.teamWithId();

        given(userTeamRepository.existsByTeamIdAndUserIdAndRole(team.getId(), notOwner.getId(), TeamRole.OWNER))
                .willReturn(false);

        // when & then
        assertThatThrownBy(() -> pageService.deleteTeamPage(team.getId(), 1L, notOwner.getId()))
                .isInstanceOf(BaseException.class)
                .satisfies(e -> assertThat(((BaseException) e).getStatus())
                        .isEqualTo(BaseResponseStatus.FAIL_AUTHENTICATE));

        then(pageRepository).should(never()).findByIdAndDirectoryTeamId(any(), any());
        then(pageRepository).should(never()).delete(any());
    }
}
