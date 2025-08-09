package com.example.trader.entity;


import com.example.trader.dto.EdgeRequestDto;
import com.example.trader.entity.base.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Entity
public class Edge extends BaseTimeEntity {

    @Id
    @GeneratedValue
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_id")
    private Node source;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_id")
    private Node target;
    @ManyToOne(fetch = FetchType.LAZY)
    private Page page;
    private String type;
    private String label;
    private String sourceHandle;
    private String targetHandle;

    public void setPage(Page page){
        this.page = page;
    }
    public void deletePage(){
        this.page = null;
    }
    // Edge 엔티티 내부
    public void setSource(Node source){
        this.source = source;
    }
    public void setTarget(Node target){
        this.target = target;
    }
    public void updateFromDto(EdgeRequestDto dto,Page page,Node source, Node target) {
        this.source = source;
        this.target = target;
        this.type = dto.getType();
        this.label = dto.getLabel();
        this.sourceHandle = dto.getSourceHandle();
        this.targetHandle = dto.getTargetHandle();
        // page 등 연관관계 변경이 필요하다면 별도 처리
        this.page = page;
    }
}
