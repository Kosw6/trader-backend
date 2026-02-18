package com.example.trader.service;

import com.example.trader.common.InviteCodeGenerator;
import com.example.trader.dto.JoinRequestDto;
import com.example.trader.dto.RequestTeamNameDto;
import com.example.trader.dto.ResponseTeamDto;
import com.example.trader.dto.TeamSummaryDto;
import com.example.trader.entity.*;
import com.example.trader.exception.BaseException;
import com.example.trader.httpresponse.BaseResponseStatus;
import com.example.trader.repository.*;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TeamService {

    private final TeamRepository teamRepository;
    private final UserRepository userRepository;
    private final UserTeamRepository utRepository;
    private final NotifycationService notificationService;
    private final JoinRequestRepository joinRequestRepository;
    //팀 생성 메서드->반환값 넣어서 팀 생성하고 응답값으로 바로 페이지에서 추가할 수 있게
    @Transactional
    public Team createTeam(Long creatorUserId, RequestTeamNameDto dto){
        User owner = userRepository.findById(creatorUserId).orElseThrow();
        final int maxAttempts = 5;

        for (int i = 0; i < maxAttempts; i++) {
            String code = InviteCodeGenerator.generate(6);

            Team team = Team.builder()
                    .teamName(dto.teamName())
                    .code(code)
                    .build();
            try {
                teamRepository.saveAndFlush(team);
                // 충돌을 여기서 바로 터뜨리기 위해 flush->DB에서 code의 유니크 제약 조건 검사,
                // 만약 save만 해서 1차 캐시+커밋시점에 insert된다면 트랜잭션 바깥이므로 재시도 로직 불가능
                team.addMember(owner, TeamRole.OWNER);
                Team createdTeam = teamRepository.saveAndFlush(team); // owner 유니크 + link 저장
                return createdTeam;
            } catch (org.springframework.dao.DataIntegrityViolationException e) {
                // 유니크 충돌일 가능성이 큼 → 재시도
            }

        }
        //TODO:상태코드 변경 중앙화 코드로
        throw new BaseException(BaseResponseStatus.FAIL_CREATE_TEAM);
    }
    //TODO:팀명 수정 메서드->update로 변경 예정?
    @Transactional
    public Team updateTeamName(Long teamId, RequestTeamNameDto dto){
        Team team = teamRepository.findById(teamId).orElseThrow(()-> new BaseException(BaseResponseStatus.INVALID_TEAM));
        team.setTeamName(dto.teamName());
        return teamRepository.save(team);
    }

    //역할 사전값 반환
    @Transactional(readOnly = true)
    public List<TeamRole> getTeamRole(){
        return List.of(TeamRole.values());
    }

    @Transactional
    public void changeTeamRole(Long teamId, Long userId, TeamRole role){

    }

    @Transactional
    public void joinTeam(Long requesterId, String code) {
        Team team = teamRepository.findByCode(code)
                .orElseThrow(() -> new BaseException(BaseResponseStatus.TEAM_NOT_FOUND, "코드와 일치하는 팀이 없습니다."));

        Long ownerUserId = teamRepository.findOwnerUserIdByTeamId(team.getId())
                .orElseThrow(() -> new BaseException(BaseResponseStatus.INTERNAL_SERVER_ERROR, "팀의 오너가 존재하지 않습니다."));

        User requester = userRepository.findById(requesterId)
                .orElseThrow(() -> new BaseException(BaseResponseStatus.USER_NOT_FOUND, "요청자를 찾을 수 없습니다."));

        //이미 팀원이면 요청 불가능
        if (utRepository.existsByTeamIdAndUserId(team.getId(),requesterId)) {
            throw new BaseException(BaseResponseStatus.ALREADY_TEAM_MEMBER);
        }

        //중복 PENDING 방지, 재요청
        if (joinRequestRepository.existsByRequesterIdAndTeamIdAndStatus(requesterId, team.getId(), JoinRequestStatus.PENDING)) {
            return; // 또는 예외
        }

        JoinRequest req = joinRequestRepository.save(JoinRequest.create(requester, team));

        notificationService.notifyTeamJoinRequested(
                ownerUserId,
                team.getId(),
                req.getId(),
                requesterId,
                team.getTeamName(),
                requester.getNickName()
        );
    }

    public void exitTeam(Long userId, Long teamId){
        Team team = teamRepository.findById(teamId).orElseThrow(()-> new BaseException(BaseResponseStatus.TEAM_NOT_FOUND));
        User user = userRepository.findById(userId).orElseThrow(()->new BaseException(BaseResponseStatus.USER_NOT_FOUND));
        team.removeMember(user);
    }

    //권한확인후 진행(승인,거절)
    //대기중인 요청에서 제거
    //approverId:SecurityContext에서 불러오기
    @Transactional
    public void checkTeamRequest(Long approverId, Long requestId,JoinRequestStatus status) {
        if (status == JoinRequestStatus.PENDING) throw new BaseException(BaseResponseStatus.INVALID_REQUEST,"해당 요청은 대기가 아닌 승인/거절이어야 합니다.");
        JoinRequest joinRequest = joinRequestRepository.findPendingWithTeamAndRequester(requestId).orElseThrow(() -> new BaseException(BaseResponseStatus.JOIN_REQUEST_NOT_FOUND));
        Team team = joinRequest.getTeam();
        // 권한 검증(OWNER)
        if (!utRepository.existsByTeamIdAndUserIdAndRole(team.getId(),approverId,TeamRole.OWNER)){
            throw new BaseException(BaseResponseStatus.ACCESS_DENIED,"권한 부족:팀의 오너만 승인이 가능합니다.");
        }

        User requester = joinRequest.getRequester();
        if (status == JoinRequestStatus.APPROVED) {

            team.addMember(requester, TeamRole.MEMBER);
            joinRequest.approve();

            notificationService.notifyTeamJoinApproved(requester.getId(), team.getId(), team.getTeamName());
        } else {
            joinRequest.reject();
            notificationService.notifyTeamJoinRejected(requester.getId(), team.getId(), team.getTeamName());
        }

        // ⭐ OWNER가 받은 "요청 알림" 읽음 처리
        notificationService.markJoinRequestNotificationAsRead(approverId, joinRequest.getId());
    }


    //팀 삭제 메서드
    @Transactional
    public void deleteTeam(Long teamId){
        teamRepository.deleteById(teamId);
    }
    //팀 조회 메서드(단일,상세 내용[팀원목록])
    @Transactional(readOnly = true)
    public ResponseTeamDto findTeam(Long teamId){
        Team team = teamRepository.findByIdWithMembers(teamId).orElseThrow();
        return ResponseTeamDto.ofDto(team);
    }
    //자신의 팀 목록 조회 메서드(id,팀명,팀인원수,오너명,생성일자)
    @Transactional(readOnly = true)
    public List<TeamSummaryDto> findMyTeamsSummary(Long userId){
        return teamRepository.findMyTeamsSummary(userId);
    }
}

