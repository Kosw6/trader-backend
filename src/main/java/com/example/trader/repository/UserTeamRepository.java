package com.example.trader.repository;

import com.example.trader.entity.TeamRole;
import com.example.trader.entity.UserTeam;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserTeamRepository extends JpaRepository<UserTeam,Long> {
    boolean existsByTeamIdAndUserIdAndRole(Long teamId, Long userId, TeamRole role);
    boolean existsByTeamIdAndUserId(Long teamId, Long userId);
    List<UserTeam> findAllByUserId(Long userId);
    Optional<UserTeam> findAllByUserIdAndTeamId(Long userId,Long teamId);
}
