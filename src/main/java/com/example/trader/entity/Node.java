package com.example.trader.entity;

import com.example.trader.dto.map.RequestNodeDto;
import com.example.trader.entity.base.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Formula;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

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


    @Basic(fetch = FetchType.LAZY)
    @Lob
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(columnDefinition = "text")
    private String content;

    @Formula("substring(n1_0.content, 1, 20)::text")
    private String contentPreview;

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
        Long targetId = note.getId();
        // 중복 방지
//        if (noteLinks.stream().anyMatch(l -> Objects.equals(l.getNoteId(), targetId))) return;
        NodeNoteLink link = NodeNoteLink.of(this, note);
        noteLinks.add(link);
    }
    public void detach(Note note) {
        Long targetId = note.getId();
//        noteLinks.removeIf(l -> Objects.equals(l.getNoteId(), targetId));
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
    /** 여러 개 한꺼번에 제거 (null 안전, Lazy 초기화 방지) */
    public void detachAll(Collection<Note> notes) {
        if (notes == null || notes.isEmpty()) return;

        // FK id만 뽑아서 비교하도록 변경
        Set<Long> targetIds = notes.stream()
                .map(Note::getId)  // getId()는 프록시 초기화 안 함
                .collect(Collectors.toSet());

        // link.getNoteId()로 비교 → Note를 로딩하지 않음
//        noteLinks.removeIf(l -> targetIds.contains(l.getNoteId()));
    }



}
