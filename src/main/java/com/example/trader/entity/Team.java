package com.example.trader.entity;

import com.example.trader.entity.base.BaseTimeEntity;
import com.example.trader.exception.BaseException;
import com.example.trader.httpresponse.BaseResponseStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
public class Team extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String teamName;

    @Builder.Default
    @OneToMany(mappedBy = "team", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<UserTeam> userTeams = new ArrayList<>();
    
    //팀 생성시에 랜덤 코드 발급
    @Column(nullable = false, unique = true, length = 6)
    private String code;

    public void addMember(User user, TeamRole role) {
        // 중복 방지 (같은 유저가 이미 팀에 있으면 예외)
        boolean exists = userTeams.stream()
                .anyMatch(ut -> ut.getUser().getId().equals(user.getId()));
        if (exists) throw new BaseException(BaseResponseStatus.ALREADY_TEAM_MEMBER);

        if (role == TeamRole.OWNER) {
            boolean ownerExists = userTeams.stream()
                    .anyMatch(ut -> ut.getRole() == TeamRole.OWNER);

            if (ownerExists) {
                throw new BaseException(BaseResponseStatus.TEAM_OWNER_CONFLICT);
            }
        }

        UserTeam link = UserTeam.create(user, this, role);
        userTeams.add(link);
        user.getUserTeams().add(link); // (양방향이면 같이)
    }

    public Team setTeamName(String name){
        this.teamName = name;
        return this;
    }
    //팀 탈퇴
    public void removeMember(User user) {
        UserTeam link = userTeams.stream()
                .filter(ut -> ut.getUser().getId().equals(user.getId()))
                .findFirst()
                .orElseThrow(() -> new BaseException(BaseResponseStatus.USER_NOT_FOUND,"팀 멤버가 아닙니다."));

        if (link.getRole() == TeamRole.OWNER) {
            throw new BaseException(BaseResponseStatus.INVALID_REQUEST,"OWNER는 탈퇴할 수 없습니다.");
        }

        userTeams.remove(link);
        user.getUserTeams().remove(link);   //양쪽 컬렉션 동기화
        link.detach();
    }
    

}
