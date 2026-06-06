package com.example.trader.service;

import com.example.trader.dto.map.RequestDirectoryDto;
import com.example.trader.dto.map.ResponseDirectoryDto;
import com.example.trader.dto.map.UpdateDirReq;
import com.example.trader.entity.Directory;
import com.example.trader.entity.Team;
import com.example.trader.entity.TeamRole;
import com.example.trader.entity.User;
import com.example.trader.exception.BaseException;
import com.example.trader.httpresponse.BaseResponseStatus;
import com.example.trader.repository.DirectoryRepository;
import com.example.trader.repository.TeamRepository;
import com.example.trader.repository.UserTeamRepository;
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
class DirectoryServiceTest {

    @Mock
    DirectoryRepository directoryRepository;

    @Mock
    TeamRepository teamRepository;

    @Mock
    UserTeamRepository userTeamRepository;

    @InjectMocks
    DirectoryService directoryService;

    //dto생성 편의 메서드
    private RequestDirectoryDto reqDto(String name, Long parentId) {
        RequestDirectoryDto dto = new RequestDirectoryDto();
        dto.setName(name);
        dto.setParentId(parentId);
        return dto;
    }

    // ── createDirectory (개인) ────────────────────

    @Test
    void parentId가_null이면_부모_없이_디렉토리가_생성된다() {
        // given
        User user = TestFixtures.userWithId();
        RequestDirectoryDto dto = reqDto("myDir", null);

        Directory saved = TestFixtures.dir(user);
        ReflectionTestUtils.setField(saved, "id", 1L);

        given(directoryRepository.save(any(Directory.class))).willReturn(saved);

        // when
        ResponseDirectoryDto result = directoryService.createDirectory(dto, user);

        // then
        assertThat(result.getName()).isEqualTo(saved.getName());
        assertThat(result.getParentId()).isNull();
        then(directoryRepository).should(never()).existsByIdAndUserId(any(), any());//parent가 없으면 부모 검증 로직을 타면 안됨
    }

    @Test
    void parentId가_유효하면_부모_포함_디렉토리가_생성된다() {
        // given
        User user = TestFixtures.userWithId();
        Long parentId = 99L;
        RequestDirectoryDto dto = reqDto("childDir", parentId);

        Directory parent = TestFixtures.dir(user);
        ReflectionTestUtils.setField(parent, "id", parentId);

        Directory saved = TestFixtures.childDir(user, parent);
        ReflectionTestUtils.setField(saved, "id", 2L);

        given(directoryRepository.existsByIdAndUserId(parentId, user.getId())).willReturn(true);
        given(directoryRepository.findById(parentId)).willReturn(Optional.of(parent));
        given(directoryRepository.save(any(Directory.class))).willReturn(saved);

        // when
        ResponseDirectoryDto result = directoryService.createDirectory(dto, user);

        // then
        assertThat(result.getParentId()).isEqualTo(parentId);
    }

    @Test
    void parentId가_있는데_소유자가_아니면_예외가_발생한다() {
        // given
        User user = TestFixtures.userWithId();
        Long parentId = 99L;
        RequestDirectoryDto dto = reqDto("childDir", parentId);

        given(directoryRepository.existsByIdAndUserId(parentId, user.getId())).willReturn(false);

        // when & then
        assertThatThrownBy(() -> directoryService.createDirectory(dto, user))
                .isInstanceOf(IllegalArgumentException.class);

        then(directoryRepository).should(never()).save(any());
    }

    // ── getDirectory (개인) ───────────────────────

    @Test
    void 존재하는_디렉토리를_조회하면_DTO가_반환된다() {
        // given
        User user = TestFixtures.userWithId();
        Directory dir = TestFixtures.dir(user);
        ReflectionTestUtils.setField(dir, "id", 1L);

        given(directoryRepository.findByIdAndUserId(1L, user.getId())).willReturn(Optional.of(dir));

        // when
        ResponseDirectoryDto result = directoryService.getDirectory(1L, user.getId());

        // then
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getName()).isEqualTo(dir.getName());
    }

    @Test
    void userId가_다른_디렉토리를_조회하면_예외가_발생한다() {
        // given
        given(directoryRepository.findByIdAndUserId(1L, 99L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> directoryService.getDirectory(1L, 99L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── updateDirectory (개인) ────────────────────

    @Test
    void 디렉토리_이름을_변경하면_정상_반영된다() {
        // given
        User user = TestFixtures.userWithId();
        Long dirId = 1L;
        UpdateDirReq dto = new UpdateDirReq("newName", null);

        Directory dir = TestFixtures.dir(user);
        ReflectionTestUtils.setField(dir, "id", dirId);

        given(directoryRepository.findByIdAndUserId(dirId, user.getId())).willReturn(Optional.of(dir));

        // when
        ResponseDirectoryDto result = directoryService.updateDirectory(dirId, dto, user.getId());

        // then
        assertThat(result.getName()).isEqualTo("newName");
        assertThat(result.getParentId()).isNull();
    }

    @Test
    void 존재하지_않는_부모로_이동하면_예외가_발생한다() {
        // given
        User user = TestFixtures.userWithId();
        Long dirId = 1L;
        Long invalidParentId = 999L;
        UpdateDirReq dto = new UpdateDirReq("name", invalidParentId);

        Directory dir = TestFixtures.dir(user);
        ReflectionTestUtils.setField(dir, "id", dirId);

        given(directoryRepository.findByIdAndUserId(dirId, user.getId())).willReturn(Optional.of(dir));
        given(directoryRepository.existsByIdAndUserId(invalidParentId, user.getId())).willReturn(false);

        // when & then
        assertThatThrownBy(() -> directoryService.updateDirectory(dirId, dto, user.getId()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── deleteDirectory (개인) ────────────────────

    @Test
    void 디렉토리를_삭제하면_repository의_delete가_호출된다() {
        // given
        Long dirId = 1L;
        Long userId = 10L;

        // when
        directoryService.deleteDirectory(dirId, userId);

        // then
        then(directoryRepository).should().deleteByIdAndUserId(dirId, userId);
    }

    // ── createTeamDirectory ───────────────────────

    @Test
    void 팀이_존재하면_팀_디렉토리가_생성된다() {
        // given
        Team team = TestFixtures.teamWithId();
        RequestDirectoryDto dto = reqDto("teamDir", null);

        Directory savedDir = Directory.builder().name("teamDir").team(team).build();
        ReflectionTestUtils.setField(savedDir, "id", 5L);

        given(teamRepository.findById(team.getId())).willReturn(Optional.of(team));
        given(directoryRepository.save(any(Directory.class))).willReturn(savedDir);

        // when
        ResponseDirectoryDto result = directoryService.createTeamDirectory(team.getId(), dto);

        // then
        assertThat(result.getName()).isEqualTo("teamDir");
        then(directoryRepository).should().save(any(Directory.class));
    }

    @Test
    void 존재하지_않는_팀ID로_생성하면_BaseException이_발생한다() {
        // given
        Long nonExistentTeamId = 999L;
        RequestDirectoryDto dto = reqDto("teamDir", null);

        given(teamRepository.findById(nonExistentTeamId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> directoryService.createTeamDirectory(nonExistentTeamId, dto))
                .isInstanceOf(BaseException.class)
                .satisfies(e -> assertThat(((BaseException) e).getStatus())
                        .isEqualTo(BaseResponseStatus.INVALID_TEAM));

        then(directoryRepository).should(never()).save(any());
    }

    // ── getTeamDirectory ──────────────────────────

    @Test
    void 팀_소속_디렉토리를_조회하면_DTO가_반환된다() {
        // given
        Team team = TestFixtures.teamWithId();
        Directory dir = Directory.builder().name("teamDir").team(team).build();
        ReflectionTestUtils.setField(dir, "id", 5L);

        given(directoryRepository.findById(5L)).willReturn(Optional.of(dir));

        // when
        ResponseDirectoryDto result = directoryService.getTeamDirectory(team.getId(), 5L);

        // then
        assertThat(result.getId()).isEqualTo(5L);
        assertThat(result.getName()).isEqualTo("teamDir");
    }

    @Test
    void 다른_팀의_디렉토리를_조회하면_FAIL_AUTHENTICATE_예외가_발생한다() {
        // given
        Team team = TestFixtures.teamWithId();
        Team otherTeam = TestFixtures.teamWithId();

        Directory dir = Directory.builder().name("teamDir").team(otherTeam).build();
        ReflectionTestUtils.setField(dir, "id", 5L);

        given(directoryRepository.findById(5L)).willReturn(Optional.of(dir));

        // when & then
        assertThatThrownBy(() -> directoryService.getTeamDirectory(team.getId(), 5L))
                .isInstanceOf(BaseException.class)
                .satisfies(e -> assertThat(((BaseException) e).getStatus())
                        .isEqualTo(BaseResponseStatus.FAIL_AUTHENTICATE));
    }

    // ── updateTeamDirectory ───────────────────────

    @Test
    void 팀_디렉토리_이름을_변경하면_정상_반영된다() {
        // given
        Team team = TestFixtures.teamWithId();
        Long dirId = 5L;
        UpdateDirReq dto = new UpdateDirReq("newName", null);

        Directory dir = Directory.builder().name("oldName").team(team).build();
        ReflectionTestUtils.setField(dir, "id", dirId);

        given(directoryRepository.findById(dirId)).willReturn(Optional.of(dir));

        // when
        ResponseDirectoryDto result = directoryService.updateTeamDirectory(team.getId(), dirId, dto);

        // then
        assertThat(result.getName()).isEqualTo("newName");
        assertThat(result.getParentId()).isNull();
    }

    @Test
    void 팀_소속이_아닌_부모로_이동하면_예외가_발생한다() {
        // given
        Team team = TestFixtures.teamWithId();
        Long dirId = 5L;
        Long invalidParentId = 999L;
        UpdateDirReq dto = new UpdateDirReq("name", invalidParentId);

        Directory dir = Directory.builder().name("dir").team(team).build();
        ReflectionTestUtils.setField(dir, "id", dirId);

        given(directoryRepository.findById(dirId)).willReturn(Optional.of(dir));
        given(directoryRepository.existsByIdAndTeamId(invalidParentId, team.getId())).willReturn(false);

        // when & then
        assertThatThrownBy(() -> directoryService.updateTeamDirectory(team.getId(), dirId, dto))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── deleteTeamDirectory ───────────────────────

    @Test
    void OWNER_권한이면_팀_디렉토리를_삭제할_수_있다() {
        // given
        Team team = TestFixtures.teamWithId();
        User owner = TestFixtures.userWithId();
        Long dirId = 5L;

        Directory dir = Directory.builder().name("teamDir").team(team).build();
        ReflectionTestUtils.setField(dir, "id", dirId);

        given(userTeamRepository.existsByTeamIdAndUserIdAndRole(team.getId(), owner.getId(), TeamRole.OWNER))
                .willReturn(true);
        given(directoryRepository.findById(dirId)).willReturn(Optional.of(dir));

        // when
        directoryService.deleteTeamDirectory(team.getId(), dirId, owner.getId());

        // then
        then(directoryRepository).should().delete(dir);
    }

    @Test
    void OWNER가_아니면_팀_디렉토리_삭제시_FAIL_AUTHENTICATE_예외가_발생한다() {
        // given
        Team team = TestFixtures.teamWithId();
        User notOwner = TestFixtures.userWithId();

        given(userTeamRepository.existsByTeamIdAndUserIdAndRole(team.getId(), notOwner.getId(), TeamRole.OWNER))
                .willReturn(false);

        // when & then
        assertThatThrownBy(() -> directoryService.deleteTeamDirectory(team.getId(), 5L, notOwner.getId()))
                .isInstanceOf(BaseException.class)
                .satisfies(e -> assertThat(((BaseException) e).getStatus())
                        .isEqualTo(BaseResponseStatus.FAIL_AUTHENTICATE));

        then(directoryRepository).should(never()).findById(any());
        then(directoryRepository).should(never()).delete(any());
    }
}
