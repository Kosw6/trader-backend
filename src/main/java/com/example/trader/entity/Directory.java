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

    // 자기참조: 상위 디렉토리
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id") // FK 컬럼명
    private Directory parent;

    // (선택) 하위 디렉토리 목록 - 양방향 매핑 원할 때
    @OneToMany(mappedBy = "parent")
    private List<Directory> children = new ArrayList<>();

    @OneToMany(mappedBy = "directory", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Page> pages = new ArrayList<>();
    // getter, setter

}
