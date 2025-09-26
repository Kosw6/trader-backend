package com.example.trader.entity;


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
public class Directory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    // 자기참조: 상위 디렉토리, + 하위 디렉토리는 cascade옵션 DB레벨에서 설정
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
