package com.example.trader.service;

import com.example.trader.dto.ResponseNoteDto;
import com.example.trader.entity.Note;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NoteBrowseService {
    private final EntityManager em;
    public record CalendarDay(LocalDate date, long count) {}
    public record CalendarMonth(String month, List<CalendarDay> days, long totalNotes) {}

    private Expression<LocalDate> noteDay(Root<Note> n, CriteriaBuilder cb) {
        return cb.coalesce(n.<LocalDate>get("noteDate"),
                ((org.hibernate.query.criteria.HibernateCriteriaBuilder) cb).cast(
                        (org.hibernate.query.criteria.JpaExpression<?>) n.get("createdAt"), LocalDate.class));
    }

    private Predicate predicates(Long owner, NoteBrowseFilter f, Root<Note> n,
                                 CriteriaQuery<?> query, CriteriaBuilder cb) {
        var predicates = new ArrayList<Predicate>();
        // Every branch is nested under the authenticated owner, including legacy ticker matches.
        predicates.add(cb.equal(n.get("user").get("id"), owner));
        if (!f.query().isEmpty()) {
            var literal = NoteBrowseFilter.literalPattern(f.query());
            predicates.add(cb.or(cb.like(cb.lower(n.get("subject")), literal, '\\'),
                    cb.like(cb.lower(n.get("content")), literal, '\\')));
        }
        if (f.start() != null) predicates.add(cb.greaterThanOrEqualTo(noteDay(n, cb), f.start()));
        if (f.end() != null) predicates.add(cb.lessThanOrEqualTo(noteDay(n, cb), f.end()));
        if (!f.tickers().isEmpty()) {
            if (f.tickerMatch() == NoteBrowseFilter.TickerMatch.ALL) {
                for (String ticker : f.tickers()) predicates.add(hasTicker(n, query, cb, List.of(ticker)));
            } else predicates.add(hasTicker(n, query, cb, f.tickers()));
        }
        return cb.and(predicates.toArray(Predicate[]::new));
    }

    private Predicate hasTicker(Root<Note> n, CriteriaQuery<?> query, CriteriaBuilder cb, List<String> tickers) {
        Subquery<Long> tagged = query.subquery(Long.class);
        Root<Note> taggedNote = tagged.from(Note.class);
        ListJoin<Note,String> tag = taggedNote.joinList("stockSymbs");
        tagged.select(taggedNote.get("id")).where(cb.equal(taggedNote.get("id"), n.get("id")),
                cb.upper(tag).in(tickers));
        return cb.or(cb.upper(n.get("stockSymb")).in(tickers), cb.exists(tagged));
    }

    public Page<ResponseNoteDto> search(Long owner, NoteBrowseFilter filter, int page, int size) {
        if (owner == null || page < 0 || page > 10000 || size < 1 || size > 20)
            throw NoteBrowseFilter.bad("페이지와 표시 개수(1~20)를 확인해주세요.");
        var cb = em.getCriteriaBuilder();
        var query = cb.createQuery(Note.class);
        var n = query.from(Note.class);
        query.select(n).where(predicates(owner, filter, n, query, cb))
                .orderBy(cb.desc(noteDay(n, cb)), cb.desc(n.get("createdAt")), cb.desc(n.get("id")));
        var notes = em.createQuery(query).setFirstResult(page * size).setMaxResults(size)
                .setHint("jakarta.persistence.query.timeout", 5000).getResultList();
        var countQuery = cb.createQuery(Long.class);
        var countRoot = countQuery.from(Note.class);
        countQuery.select(cb.count(countRoot)).where(predicates(owner, filter, countRoot, countQuery, cb));
        long total = em.createQuery(countQuery).setHint("jakarta.persistence.query.timeout", 5000).getSingleResult();
        return new PageImpl<>(notes.stream().map(ResponseNoteDto::fromEntity).toList(), PageRequest.of(page,size),total);
    }

    public CalendarMonth calendar(Long owner, YearMonth month, NoteBrowseFilter filter) {
        if (owner == null || month == null || month.getYear() < 1 || month.getYear() > 9999)
            throw NoteBrowseFilter.bad("조회할 월을 YYYY-MM 형식으로 지정해주세요.");
        var days=calendarDays(owner,month.atDay(1),month.atEndOfMonth(),filter);
        return new CalendarMonth(month.toString(),days,days.stream().mapToLong(CalendarDay::count).sum());
    }

    public List<CalendarDay> calendarDays(Long owner,LocalDate start,LocalDate end,NoteBrowseFilter filter) {
        if(owner==null || start==null || end==null || start.isAfter(end) || end.getYear()-start.getYear()>9)
            throw NoteBrowseFilter.bad("캘린더 조회 범위를 확인해주세요.");
        var cb = em.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        var n = query.from(Note.class);
        var day = noteDay(n,cb);
        query.multiselect(day,cb.count(n)).where(predicates(owner,filter,n,query,cb),
                cb.between(day,start,end)).groupBy(day).orderBy(cb.asc(day));
        var days = em.createQuery(query).setHint("jakarta.persistence.query.timeout",5000).getResultList().stream()
                .map(t -> new CalendarDay(t.get(0,LocalDate.class),t.get(1,Long.class))).toList();
        return days;
    }
}
