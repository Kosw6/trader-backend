package com.example.trader.entity;


import com.example.trader.entity.base.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Entity
public class Directory extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;

    //그룹단위면 null가능
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    // 팀 디렉토리일 경우 team 존재
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    private Team team;

    // 자기참조: 상위 디렉토리, + 하위 디렉토리는 cascade옵션 DB레벨에서 설정, null허용
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id") // FK 컬럼명
    private Directory parent;

    public void rename(String name){
        this.name = name;
    }
    public void setParent(Directory parent){
        this.parent = parent;
    }
}
