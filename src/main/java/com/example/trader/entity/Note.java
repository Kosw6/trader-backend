package com.example.trader.entity;


import com.example.trader.entity.base.BaseTimeEntity;
import jakarta.annotation.Nullable;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

@Entity
@Getter
@Table(name = "note")
@NoArgsConstructor(access = AccessLevel.PROTECTED)//JPA의 지연로딩을 위해 필요함, 빈 객체 생성후 값 주입 방식
@AllArgsConstructor
@EqualsAndHashCode
@Builder
public class Note extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY,optional = false)
    @JoinColumn(name = "user_id",nullable = false)
    private User user;
    private Long teamId;
    private String subject;
    // 긴 노트 원문을 보존한다. 기본 varchar(255)나 PostgreSQL LOB OID로 저장하지 않는다.
    @Column(columnDefinition = "text")
    private String content;
    private String stockSymb;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "note_stock_tag", joinColumns = @JoinColumn(name = "note_id"))
    @OrderColumn(name = "tag_order")
    @Column(name = "symbol", nullable = false, length = 64)
    @org.hibernate.annotations.BatchSize(size = 50)
    @Builder.Default
    private List<String> stockSymbs = new ArrayList<>();
    private LocalDate noteDate;

    @OneToMany(mappedBy = "note")   // 소유자는 링크 엔티티
    private final List<NodeNoteLink> nodeLinks = new ArrayList<>();

    public void setUser(User userId){
        this.user = user;
    }
    public void setTeam(Long teamId){
        this.teamId = teamId;
    }
    public void changeContent(String content){
        this.content = content;
    }
    public void changeSubject(String subject){
        this.subject = subject;
    }
    public void changeNoteDate(LocalDate noteDate) {
        this.noteDate = Objects.requireNonNull(noteDate, "기록 날짜를 입력해주세요.");
    }
    public void changestockSymb(String stockSymb){
        changeStockSymbs(stockSymb == null || stockSymb.isBlank() ? List.of() : List.of(stockSymb));
    }

    public List<String> getStockSymbs() {
        // No bulk rewrite is needed for existing single-ticker notes.
        if (stockSymbs == null || stockSymbs.isEmpty())
            return stockSymb == null || stockSymb.isBlank() ? List.of() : List.of(stockSymb);
        return List.copyOf(stockSymbs);
    }

    public void changeStockSymbs(List<String> symbols) {
        var normalized = NoteTickers.normalize(symbols);
        stockSymbs.clear();
        stockSymbs.addAll(normalized);
        stockSymb = normalized.isEmpty() ? null : normalized.get(0);
    }

    public void applyTickerRequest(List<String> symbols, String legacySymbol) {
        if (symbols != null) changeStockSymbs(symbols);
        else if (!Objects.equals(stockSymb, legacySymbol)) {
            // An old client editing the text must not silently erase secondary tags.
            if (getStockSymbs().size() > 1)
                throw new IllegalArgumentException("여러 티커가 연결된 노트는 최신 편집 화면에서 수정해주세요.");
            changestockSymb(legacySymbol);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Note note)) return false;
        return id.equals(note.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
