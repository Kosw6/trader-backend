package com.example.trader.entity;

import com.example.trader.dto.NodeRequestDto;
import com.example.trader.entity.base.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Entity
public class Node extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private double x;
    private double y;
    private String subject;
    private String content;
    private String symb;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "note_id")
    private Note note;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "page_id")
    private Page page;


    public void setPage(Page page){
        this.page = page;
    }
    public void deletePage(){
        this.page = null;
    }

    public void updateFromDto(NodeRequestDto dto, Page page,Note note) {
        this.x = dto.getX();
        this.y = dto.getY();
        this.subject = dto.getSubject();
        this.content = dto.getContent();
        this.symb = dto.getSymb();
        this.page = page;
        this.note = note;
    }
}
