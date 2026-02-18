package com.example.trader.repository;

import com.example.trader.entity.Team;

import com.example.trader.dto.TeamSummaryDto;
import com.example.trader.entity.TeamRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TeamRepository extends JpaRepository<Team,Long> {

    //해당 팀 + 모든 유저 상세 정보
    @Query("""
    select t from Team t
    join fetch t.userTeams ut
    join fetch ut.user
    where t.id = :teamId
    """)
    Optional<Team> findByIdWithMembers(@Param("teamId") Long teamId);

    //유저의 모든 팀의 id, 팀명, 그룹원수,팀의 오너(오너는 무조건 한명),생성일자를 리스트로 받음 -> 추후 팀 목록 페이지에서 사용
    @Query("""
    select new com.example.trader.dto.TeamSummaryDto(
        t.id,
        t.teamName,
        count(utAll.id),
        owner.username,
        t.createdAt
    )
    from UserTeam utMe
    join utMe.team t
    
    left join UserTeam utAll
        on utAll.team.id = t.id
    
    join UserTeam utOwner
        on utOwner.team.id = t.id
       and utOwner.role = com.example.trader.entity.TeamRole.OWNER
    join utOwner.user owner
    
    where utMe.user.id = :userId
    group by t.id, t.teamName, owner.username, t.createdAt
    order by t.createdAt desc
    """)
    List<TeamSummaryDto> findMyTeamsSummary(@Param("userId") Long userId);

    Optional<Team> findByCode(String code);

    //해당 팀 Owner의 UserId를 반환
    @Query("""
    select ut.user.id
    from UserTeam ut
    where ut.team.id = :teamId
    and ut.role = com.example.trader.entity.TeamRole.OWNER
    """)
    Optional<Long> findOwnerUserIdByTeamId(@Param("teamId") Long teamId);

    @Modifying(clearAutomatically = true)
    @Query("""
    update UserTeam ut
    set ut.role = :role
    where ut.user.id = :userId
      and ut.team.id = :teamId
    """)
    void updateMemberRole(@Param("userId") Long userId,@Param("teamId") Long teamId,@Param("role") TeamRole role);
}
