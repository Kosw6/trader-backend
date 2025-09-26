package com.example.trader.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

@Entity
@Table(name = "node_note_link",
        uniqueConstraints = @UniqueConstraint(name = "uq_node_note", columnNames = {"node_id","note_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NodeNoteLink {
    @Id
    @GeneratedValue
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "node_id",
            foreignKey = @ForeignKey(name = "fk_link_node"))
    private Node node;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "note_id",
            foreignKey = @ForeignKey(name = "fk_link_note"))
    private Note note;

    // (옵션) 메타필드들: anchorX, anchorY ...

    public static NodeNoteLink of(Node node, Note note) {
        NodeNoteLink l = new NodeNoteLink();
        l.node = node;
        l.note = note;
        return l;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NodeNoteLink that)) return false;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
