package com.example.trader.entity;

import com.example.trader.entity.base.BaseTimeEntity;
import com.example.trader.exception.BaseException;
import com.example.trader.httpresponse.BaseResponseStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Table(
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_user_team", columnNames = {"user_id", "team_id"})
        }
)
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Getter
public class UserTeam extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id",nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id",nullable = false)
    private Team team;

    //부분 유니크 인덱스로 보장 Owner는 무조건 한명
    /*
    * CREATE UNIQUE INDEX ux_team_owner
      ON user_team (team_id)
      WHERE role = 'OWNER';
    * */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TeamRole role;

    //팀내 역할 수정
    public TeamRole changeRole(TeamRole role){
        if(this.role == TeamRole.OWNER){
            throw new BaseException(BaseResponseStatus.TEAM_ROLE_CHANGE_DENIED,"오너는 역할을 변경할 수 없습니다.");
        }
        this.role = role;
        return this.role;
    }

    public static UserTeam create(User user, Team team, TeamRole role) {
        UserTeam ut = new UserTeam();
        ut.user = user;
        ut.team = team;
        ut.role = role;
        return ut;
    }
    public void detach() {
        this.user = null;
        this.team = null;
    }

}
