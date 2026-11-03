package com.example.trader.entity;

import com.example.trader.service.NoteCalendarPeriod;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access=AccessLevel.PROTECTED)
@Table(name="note_calendar_performance",uniqueConstraints=@UniqueConstraint(name="uq_note_calendar_performance_owner_period",columnNames={"user_id","unit","period_start"}))
public class NoteCalendarPerformance {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false)
    @JoinColumn(name="user_id",nullable=false)
    @org.hibernate.annotations.OnDelete(action=org.hibernate.annotations.OnDeleteAction.CASCADE)
    private User user;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=5) private NoteCalendarPeriod.Unit unit;
    @Column(name="period_start",nullable=false) private LocalDate periodStart;
    @Column(precision=14,scale=2) private BigDecimal amount;
    @Column(precision=10,scale=4) private BigDecimal rate;
    @Column(nullable=false,length=3) private String currency;
    @ElementCollection
    @CollectionTable(name="note_calendar_tag",joinColumns=@JoinColumn(name="record_id"),
        foreignKey=@ForeignKey(name="fk_note_calendar_tag_record",foreignKeyDefinition="foreign key (record_id) references note_calendar_performance (id) on delete cascade"))
    @OrderColumn(name="tag_order")
    @Column(name="label",nullable=false,length=80)
    private List<String> tags=new ArrayList<>();
    @Version private Long version;
    public NoteCalendarPerformance(User user,NoteCalendarPeriod period) {
        this.user=user;this.unit=period.unit();this.periodStart=period.start();
    }
    public void changeTags(List<String> values) {
        tags.clear();tags.addAll(values);
    }
    public void change(BigDecimal amount,BigDecimal rate,String currency) {
        this.amount=amount;this.rate=rate;this.currency=currency;
    }
}
