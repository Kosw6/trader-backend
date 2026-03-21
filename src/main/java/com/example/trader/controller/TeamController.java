package com.example.trader.controller;

import com.example.trader.dto.*;
import com.example.trader.entity.JoinRequestStatus;
import com.example.trader.entity.Team;
import com.example.trader.entity.TeamRole;
import com.example.trader.security.details.UserContext;
import com.example.trader.service.TeamService;
import com.example.trader.service.UserTeamService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/team")
@RequiredArgsConstructor
public class TeamController {
    private final TeamService teamService;
    private final UserTeamService utService;

    //자신이 속한 팀 요약 목록 반환->팀 목록 페이지
    @GetMapping("/myTeams")
    public ResponseEntity<List<TeamSummaryDto>> getAllMyTeam(@AuthenticationPrincipal UserContext user){
        return ResponseEntity.ok(teamService.findMyTeamsSummary(user.getUserDto().getId()));
    }

    //팀 상세 정보 반환->합류한 멤버 목록 등->해당 상세 페이지에서 추방, 권한 수정등 작업
    @GetMapping("/{teamId}")
    public ResponseEntity<ResponseTeamDto> getTeamDetail(@AuthenticationPrincipal UserContext user, @PathVariable Long teamId){
        //권한검사
        utService.checkTeamAuth(user.getUserDto().getId(), teamId);
        return ResponseEntity.ok(teamService.findTeam(teamId));
    }

    //팀 생성
    @PostMapping
    public ResponseEntity<TeamSummaryDto> createTeam(@AuthenticationPrincipal UserContext context, @RequestBody RequestTeamNameDto dto){
        Team team = teamService.createTeam(context.getUserDto().getId(), dto);
        return ResponseEntity.ok(TeamSummaryDto.of(team,1L,context.getUserDto().getNickName()));
    }

    //팀명 수정
    @PatchMapping("/{teamId}")
    public ResponseEntity<ResponseTeamDto> updateTeamName(@AuthenticationPrincipal UserContext context,@PathVariable Long teamId,@RequestBody RequestTeamNameDto dto){
        utService.checkTeamAuthWithOwner(context.getUserDto().getId(), teamId);
        return ResponseEntity.ok(ResponseTeamDto.ofDto(teamService.updateTeamName(teamId,dto)));
    }
    //팀 삭제
    @DeleteMapping("/{teamId}")
    public ResponseEntity<Void> deleteTeam(@AuthenticationPrincipal UserContext context,Long teamId){
        //권한검사-오너만
        utService.checkTeamAuthWithOwner(context.getUserDto().getId(), teamId);
        teamService.deleteTeam(teamId);
        return ResponseEntity.noContent().build();
    }
    //팀 합류 요청
    @PostMapping("/teams/join-requests")
    public ResponseEntity<Void> requestJoinTeam(
            @AuthenticationPrincipal UserContext context,
            @RequestBody JoinRequestDto dto
    ){
        teamService.joinTeam(context.getUserDto().getId(), dto.code());
        return ResponseEntity.ok().build();
    }

    //팀원 역할 변경
    @PatchMapping("/roles")
    public ResponseEntity<Void> patchUserTeamRole(@AuthenticationPrincipal UserContext context, @RequestBody UpdateUserTeamRoleDto dto){
        //오너 권한 확인
        utService.checkTeamAuthWithOwner(context.getUserDto().getId(), dto.teamId());
        teamService.changeTeamRole(dto.teamId(),dto.userId(), dto.role());
        return ResponseEntity.noContent().build();
    }



    //팀원 추방
    @DeleteMapping("/teams/{teamId}/members/{userId}")
    public ResponseEntity<Void> kickTeamMember(
            @AuthenticationPrincipal UserContext context,
            @PathVariable Long teamId,
            @PathVariable Long userId
    ){
        //오너 권한 확인
        utService.checkTeamAuthWithOwner(context.getUserDto().getId(), teamId);
        teamService.exitTeam(userId,teamId);
        return ResponseEntity.noContent().build();
    }

    //팀 탈퇴
    @DeleteMapping("/teams/{teamId}/members/me")
    public ResponseEntity<Void> leaveTeam(
            @AuthenticationPrincipal UserContext context,
            @PathVariable Long teamId
    ){
        teamService.exitTeam(context.getUserDto().getId(), teamId);
        return ResponseEntity.noContent().build();
    }

    // 오너: 팀별 대기 요청 목록 조회
    @GetMapping("/teams/{teamId}/join-requests")
    public ResponseEntity<List<PendingJoinRequestDto>> getPendingRequests(
            @AuthenticationPrincipal UserContext context,
            @PathVariable Long teamId
    ) {
        utService.checkTeamAuthWithOwner(context.getUserDto().getId(), teamId);
        return ResponseEntity.ok(teamService.getPendingJoinRequests(teamId));
    }

    // 본인: 내가 보낸 요청 상태 조회
    @GetMapping("/teams/{teamId}/join-requests/me")
    public ResponseEntity<?> getMyJoinRequest(
            @AuthenticationPrincipal UserContext context,
            @PathVariable Long teamId
    ) {
        return teamService.getMyJoinRequest(context.getUserDto().getId(), teamId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    //팀원 합류 승인/거절
    @PatchMapping("/teams/join-requests/{joinRequestId}")
    public ResponseEntity<Void> updateJoinRequestStatus(
            @AuthenticationPrincipal UserContext context,
            @PathVariable Long joinRequestId,
            @RequestBody UpdateJoinRequestStatusDto dto
    ){
        teamService.checkTeamRequest(context.getUserDto().getId(),joinRequestId,dto.status());
        return ResponseEntity.noContent().build();
    }


}
