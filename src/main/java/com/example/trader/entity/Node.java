package com.example.trader.entity;

import com.example.trader.dto.map.RequestNodeDto;
import com.example.trader.entity.base.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.*;

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
    private LocalDate recordDate;

    @OneToMany(mappedBy = "node", cascade = CascadeType.ALL, orphanRemoval = true)
    private final List<NodeNoteLink> noteLinks = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "page_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_node_page"))
    private Page page;

    public void setPage(Page page){
        this.page = page;
    }

    public void updateBasics(RequestNodeDto dto) {
        if (dto.getX() != null) this.x = dto.getX();
        if (dto.getY() != null) this.y = dto.getY();
        if (dto.getSubject() != null) this.subject = dto.getSubject();
        if (dto.getContent() != null) this.content = dto.getContent();
        if (dto.getSymb() != null) this.symb = dto.getSymb();
        if (dto.getRecordDate() != null) this.recordDate = dto.getRecordDate();
    }
    public void attach(Note note) {
        // 중복 방지
        if (noteLinks.stream().anyMatch(l -> l.getNote().equals(note))) return;
        NodeNoteLink link = NodeNoteLink.of(this, note);
        noteLinks.add(link);
    }
    public void detach(Note note) {
        noteLinks.removeIf(l -> l.getNote().equals(note));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Node node)) return false;
        return id.equals(node.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    /** 여러 개 한꺼번에 추가 (null/중복 안전) */
    public void attachAll(Collection<Note> notes) {
        if (notes == null || notes.isEmpty()) return;
        for (Note n : notes) {
            attach(n); // 중복 체크는 attach 단건에서 처리
        }
    }
    /** 여러 개 한꺼번에 제거 (null 안전) */
    public void detachAll(Collection<Note> notes) {
        if (notes == null || notes.isEmpty()) return;
        Set<Note> targets = new HashSet<>(notes);
        noteLinks.removeIf(l -> targets.contains(l.getNote()));
    }



}
