package com.example.trader.service;

import com.example.trader.dto.ResponseUserTeamDto;
import com.example.trader.entity.TeamRole;
import com.example.trader.exception.BaseException;
import com.example.trader.httpresponse.BaseResponseStatus;
import com.example.trader.repository.UserTeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserTeamService {
    private final UserTeamRepository repository;

    @Transactional(readOnly = true)
    public List<ResponseUserTeamDto> findAllUserTeam(Long userId){
        return repository.findAllByUserId(userId).stream().map(ResponseUserTeamDto::from).toList();
    }

    @Transactional(readOnly = true)
    public ResponseUserTeamDto findUserTeamByUserIdAndTeamId(Long userId,Long teamId){
        return ResponseUserTeamDto.from(repository.findAllByUserIdAndTeamId(userId,teamId).orElseThrow(()->new BaseException(BaseResponseStatus.INVALID_USER)));
    }

    @Transactional(readOnly = true)
    public void checkTeamAuth(Long userId,Long teamId){
        if(!repository.existsByTeamIdAndUserId(teamId,userId)){
            throw new BaseException(BaseResponseStatus.ACCESS_DENIED);
        }else{
            return;
        }
    }

    @Transactional(readOnly = true)
    public void checkTeamAuthWithOwner(Long userId,Long teamId){
        if(!repository.existsByTeamIdAndUserIdAndRole(teamId,userId, TeamRole.OWNER)){
            throw new BaseException(BaseResponseStatus.ACCESS_DENIED);
        }else{
            return;
        }
    }
}
