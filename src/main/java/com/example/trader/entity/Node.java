package com.example.trader.entity;

import com.example.trader.entity.base.BaseTimeEntity;
import com.example.trader.exception.InvalidNodeChangeRequestException;
import com.example.trader.exception.NodeChangeErrorCode;
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

    @Column(nullable = false)
    private double x;
    @Column(nullable = false)
    private double y;
    private String subject;


    @Lob
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(columnDefinition = "text")
    private String content;

    //목록조회에서는 NodeSummary에서 content대신 해당 필드를 조회
    @Column(name = "content_preview", length = 20)
    private String contentPreview;


    private String symb;
    private LocalDate recordDate;

    @OneToMany(mappedBy = "node", cascade = CascadeType.ALL, orphanRemoval = true)
    private final List<NodeNoteLink> noteLinks = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "page_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_node_page"))
    private Page page;

    /**
     * 팀 노드 편집 버전 (충돌 감지용).
     * updateTeamNode() 성공 시 수동으로 increment.
     * 개인 노드는 사용하지 않음 (0 유지).
     */
    @Column(nullable = false)
    private int version = 0;

    /** DB 저장 시점의 동시 갱신만 검출하는 기술 버전. 업무 version과 분리한다. */
    @Version
    @Column(name = "lock_version", nullable = false)
    private Long lockVersion;

    public void setPage(Page page){
        this.page = page;
    }

    /** 팀 노드 수정 성공 시 버전 증가 */
    public void incrementVersion() {
        this.version++;
    }

    /**
     * 검증된 내용 변경을 원자적으로 적용한다.
     *
     * @param desiredNotes noteIds 변경 시 목표 Note 집합, 그 외에는 빈 컬렉션
     * @param replaceableNoteIds 이번 요청이 교체할 수 있는 기존 Note ID 집합
     * @param versionPolicy 팀 변경은 증가, 개인 변경은 기존 정책 보존
     */
    public NodeChangeResult applyContentChange(
            NodeContentChange change,
            Collection<Note> desiredNotes,
            Set<Long> replaceableNoteIds,
            NodeVersionPolicy versionPolicy
    ) {
        validateContentChange(change, desiredNotes, replaceableNoteIds);

        int previousVersion = version;
        if (change.isEmpty()) {
            return new NodeChangeResult(previousVersion, previousVersion, List.of());
        }

        if (change.contains(NodeContentField.SUBJECT)) {
            this.subject = (String) change.value(NodeContentField.SUBJECT);
        }
        if (change.contains(NodeContentField.CONTENT)) {
            this.content = (String) change.value(NodeContentField.CONTENT);
        }
        if (change.contains(NodeContentField.SYMBOL)) {
            this.symb = (String) change.value(NodeContentField.SYMBOL);
        }
        if (change.contains(NodeContentField.RECORD_DATE)) {
            this.recordDate = (LocalDate) change.value(NodeContentField.RECORD_DATE);
        }
        if (change.contains(NodeContentField.NOTE_IDS)) {
            synchronizeNotes(desiredNotes, replaceableNoteIds);
        }

        if (versionPolicy == NodeVersionPolicy.ADVANCE_ON_CHANGE) {
            incrementVersion();
        }
        return new NodeChangeResult(previousVersion, version, change.fieldNames());
    }

    private void validateContentChange(
            NodeContentChange change,
            Collection<Note> desiredNotes,
            Set<Long> replaceableNoteIds
    ) {
        if (change.contains(NodeContentField.SUBJECT)) {
            String value = (String) change.value(NodeContentField.SUBJECT);
            if (value == null) {
                throw new InvalidNodeChangeRequestException(NodeChangeErrorCode.NULL_VALUE_NOT_ALLOWED);
            }
            if (value.isEmpty()) {
                throw new InvalidNodeChangeRequestException(NodeChangeErrorCode.EMPTY_SUBJECT_NOT_ALLOWED);
            }
        }
        if (change.contains(NodeContentField.CONTENT)
                && change.value(NodeContentField.CONTENT) == null) {
            throw new InvalidNodeChangeRequestException(NodeChangeErrorCode.NULL_VALUE_NOT_ALLOWED);
        }
        if (change.contains(NodeContentField.RECORD_DATE)
                && change.value(NodeContentField.RECORD_DATE) == null) {
            throw new InvalidNodeChangeRequestException(NodeChangeErrorCode.NULL_VALUE_NOT_ALLOWED);
        }
        if (change.contains(NodeContentField.NOTE_IDS)
                && (desiredNotes == null || replaceableNoteIds == null)) {
            throw new IllegalArgumentException("noteIds 변경에는 해석된 Note 집합이 필요합니다.");
        }
    }

    private void synchronizeNotes(Collection<Note> desiredNotes, Set<Long> replaceableNoteIds) {
        Set<Long> desiredIds = desiredNotes.stream()
                .map(Note::getId)
                .collect(Collectors.toSet());

        noteLinks.removeIf(link -> replaceableNoteIds.contains(link.getNoteId())
                && !desiredIds.contains(link.getNoteId()));
        attachAll(desiredNotes);
    }

    public void attach(Note note) {
        Long targetId = note.getId();
        if (noteLinks.stream().anyMatch(l -> Objects.equals(l.getNoteId(), targetId))) return;
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
        noteLinks.removeIf(link -> targetIds.contains(link.getNoteId()));
    }

    private static String makePreview(String content) {
        if (content == null) return null;
        return content.length() <= 20 ? content : content.substring(0, 20);
    }

    @PrePersist
    @PreUpdate
    private void syncContentPreview() {
        this.contentPreview = makePreview(this.content);
    }


}
