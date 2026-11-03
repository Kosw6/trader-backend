package com.example.trader.repository;

import com.example.trader.entity.TeamRole;
import com.example.trader.entity.UserTeam;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserTeamRepository extends JpaRepository<UserTeam,Long> {
    boolean existsByTeamIdAndUserIdAndRole(Long teamId, Long userId, TeamRole role);
    boolean existsByTeamIdAndUserId(Long teamId, Long userId);
    @Query("select ut.user.id from UserTeam ut where ut.team.id = :teamId")
    List<Long> findAuthorizedUserIds(@Param("teamId") Long teamId);
    List<UserTeam> findAllByUserId(Long userId);
    Optional<UserTeam> findAllByUserIdAndTeamId(Long userId,Long teamId);
}
