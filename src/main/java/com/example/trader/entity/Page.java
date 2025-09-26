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
public class Page {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String title;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "directory_id")
    private Directory directory;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    //TODO:너무 무거워짐 -> DB레벨에서 orphan등 걸기
//    @OneToMany(mappedBy = "page",cascade = CascadeType.ALL, orphanRemoval = true)
//    private List<Node> nodeList = new ArrayList<>();
//    @OneToMany(mappedBy = "page",cascade = CascadeType.ALL, orphanRemoval = true)
//    private List<Edge> edgeList = new ArrayList<>();
//    public void addNode(Node node){
//        nodeList.add(node);
//        node.setPage(this);
//    }
//    public void removeNode(Node node) {
//        nodeList.remove(node);    // 부모 컬렉션에서 자식 제거
//        node.deletePage();       // 자식의 부모 참조를 null로 설정하여 연관관계 끊기
//    }
//
//    public void addEdge(Edge edge){
//        edgeList.add(edge);
//        edge.setPage(this);
//    }
//    public void removeEdge(Edge edge) {
//        edgeList.remove(edge);    // 부모 컬렉션에서 자식 제거
//        edge.deletePage();       // 자식의 부모 참조를 null로 설정하여 연관관계 끊기
//    }
    public void setTitle(String title){
        this.title = title;
    }
    public void setDir(Directory dir){
        this.directory = dir;
    }

}
