package com.example.trader.service;

import com.example.trader.dto.map.*;
import com.example.trader.entity.*;
import com.example.trader.realtime.outbox.TeamTreeChangeStager;
import com.example.trader.repository.*;
import com.example.trader.security.service.SecurityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class TeamTreeMutationTest {
    DirectoryRepository dirs = mock(DirectoryRepository.class);
    PageRepository pages = mock(PageRepository.class);
    TeamRepository teams = mock(TeamRepository.class);
    UserTeamRepository members = mock(UserTeamRepository.class);
    SecurityService security = mock(SecurityService.class);
    TeamTreeChangeStager changes = mock(TeamTreeChangeStager.class);
    DirectoryService directories = new DirectoryService(dirs, teams, members, changes);
    PageService pageService = new PageService(pages, dirs, security, members, changes);
    Team team = Team.builder().id(1L).build();
    User user = User.builder().id(10L).email("tree-test@example.invalid").username("tree-test")
            .loginId("tree-test").password("fixture-only").nickName("tree-test").role(Role.USER).build();
    Directory parent = Directory.builder().id(2L).team(team).name("parent").build();
    Directory child = Directory.builder().id(3L).team(team).parent(parent).name("child").build();
    Page page = Page.builder().id(4L).directory(child).user(user).title("page").build();

    @BeforeEach void prepare() {
        when(teams.findById(1L)).thenReturn(Optional.of(team));
        when(dirs.findByIdAndTeamId(2L, 1L)).thenReturn(Optional.of(parent));
        when(dirs.existsByIdAndTeamId(2L, 1L)).thenReturn(true);
        when(dirs.findById(2L)).thenReturn(Optional.of(parent));
        when(dirs.findById(3L)).thenReturn(Optional.of(child));
        when(pages.findByIdAndDirectoryTeamId(4L, 1L)).thenReturn(Optional.of(page));
        when(security.getAuthenticationUser()).thenReturn(user);
        when(members.existsByTeamIdAndUserIdAndRole(1L, 10L, TeamRole.OWNER)).thenReturn(true);
    }

    @Test void nestedDirectoryCreationPreservesParentAndStagesOnce() {
        RequestDirectoryDto dto = new RequestDirectoryDto(); dto.setName("new"); dto.setParentId(2L);
        directories.createTeamDirectory(1L, dto);
        verify(dirs).save(argThat(d -> d.getParent() == parent && d.getTeam() == team));
        verify(changes).stage(1L);
    }
    @Test void directoryRenameMoveAndDeleteEachStageOnce() {
        directories.updateTeamDirectory(1L, 3L, new UpdateDirReq("renamed", 2L));
        assertThat(child.getParent()).isSameAs(parent);
        assertThat(child.getName()).isEqualTo("renamed");
        directories.deleteTeamDirectory(1L, 3L, 10L);
        verify(changes, times(2)).stage(1L);
    }
    @Test void rejectsDirectoryCyclesAndCrossTeamParentsWithoutEvent() {
        when(dirs.findDirectorySubtreeIdsInTeam(3L, 1L)).thenReturn(List.of(3L, 8L));
        assertThatThrownBy(() -> directories.updateTeamDirectory(1L, 3L, new UpdateDirReq("bad", 8L)))
                .isInstanceOf(IllegalArgumentException.class);
        RequestDirectoryDto dto = new RequestDirectoryDto(); dto.setName("bad"); dto.setParentId(99L);
        assertThatThrownBy(() -> directories.createTeamDirectory(1L, dto)).isInstanceOf(RuntimeException.class);
        verifyNoInteractions(changes);
        assertThat(child.getName()).isEqualTo("child");
    }
    @Test void pageCreateRenameMoveAndDeleteAllStage() {
        RequestPageDto dto = new RequestPageDto(); dto.setTitle("new"); dto.setDirectoryId(2L);
        pageService.createTeamPage(1L, dto, 10L);
        pageService.updateTeamPage(1L, 4L, new UpdatePageReq("renamed"));
        assertThat(pageService.teamPageMove(1L, 4L, 2L)).isEqualTo(3L);
        assertThat(page.getDirectory()).isSameAs(parent);
        pageService.deleteTeamPage(1L, 4L, 10L);
        verify(changes, times(4)).stage(1L);
    }
    @Test void unauthorizedDeleteAndMoveStageNothing() {
        assertThatThrownBy(() -> pageService.deleteTeamPage(1L, 4L, 99L)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> pageService.teamPageMove(1L, 4L, 99L)).isInstanceOf(RuntimeException.class);
        verifyNoInteractions(changes);
    }
    @Test void subtreeDeleteStagesOnceAndMoveCoordinatorUsesTeamMethodNotPersonalMove() {
        var init = mock(TeamInitQuery.class);
        var service = mock(PageService.class);
        var coordinator = new TeamContentCoordinator(service, init, dirs, pages, members, changes);
        when(dirs.findDirectorySubtreeIdsInTeam(3L, 1L)).thenReturn(List.of(3L, 8L));
        when(pages.findPageIdsInDirectoryTreeInTeam(3L, 1L)).thenReturn(List.of(4L));
        coordinator.deleteDirectoryWithPageIds(1L, 3L, 10L);
        verify(dirs).deleteById(3L); verify(changes).stage(1L);
        when(service.teamPageMove(1L, 4L, 2L)).thenReturn(3L);
        when(init.reloadDirectoryWithPages(eq(1L), anyLong())).thenReturn(
                new DirectoryWithPagesDto(ResponseDirectoryDto.ofDto(parent), List.of()));
        coordinator.movePageAndReload(1L, 4L, 2L);
        verify(service).teamPageMove(1L, 4L, 2L);
        verify(service, never()).move(anyLong(), anyLong(), anyLong());
        // Nested PageService transaction stages the move, not the coordinator twice.
        verify(changes, times(1)).stage(1L);
    }
}
