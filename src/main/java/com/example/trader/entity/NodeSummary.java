package com.example.trader.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "node")
@Immutable
@Getter
@NoArgsConstructor
public class NodeSummary {

    @Id
    private Long id;

    private double x;
    private double y;
    private String subject;

    @Column(name = "content_preview")
    private String contentPreview;

    private String symb;
    private LocalDate recordDate;

    @Column(name = "page_id", insertable = false, updatable = false)
    private Long pageId;

    private LocalDateTime createdAt;
    private LocalDateTime modifiedAt;

    @Column(nullable = false)
    private int version;

    @OneToMany(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "node_id",
            referencedColumnName = "id",
            insertable = false,
            updatable = false
    )
    private List<NodeNoteLink> noteLinks = new ArrayList<>();
}