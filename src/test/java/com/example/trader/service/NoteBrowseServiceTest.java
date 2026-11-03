package com.example.trader.service;

import com.example.trader.entity.Note;
import com.example.trader.entity.User;
import com.example.trader.support.fixtures.TestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

@DataJpaTest(properties={"spring.jpa.hibernate.ddl-auto=create-drop","spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"})
@Import(NoteBrowseService.class)
class NoteBrowseServiceTest {
    @Autowired TestEntityManager em;
    @Autowired NoteBrowseService browse;
    Note note(User owner,String title,List<String> tags,LocalDate day) {
        var n=TestFixtures.note(owner,null);n.changeSubject(title);n.changeContent("금리와 실적을 검토한 본문");
        n.changeStockSymbs(tags);ReflectionTestUtils.setField(n,"noteDate",day);
        ReflectionTestUtils.setField(n,"createdAt",LocalDateTime.of(2026,11,3,12,30));
        return em.persistAndFlush(n);
    }
    NoteBrowseFilter f(String q, List<String> tags,NoteBrowseFilter.TickerMatch match) {
        return new NoteBrowseFilter(q,null,null,tags,match);
    }
    @Test void anyAllCaseAndLegacyTickerAreOwnerScopedAndNeverDuplicate() {
        var owner=em.persist(TestFixtures.user());var other=em.persist(TestFixtures.user());
        var both=note(owner,"비교",List.of("NVDA","AAPL"),LocalDate.of(2026,11,2));
        var legacy=TestFixtures.note(owner,null);legacy.changeSubject("이전 노트");legacy.changeNoteDate(LocalDate.of(2026,11,1));em.persistAndFlush(legacy);
        note(owner,"다른 종목",List.of("MSFT"),LocalDate.of(2026,11,1));
        note(other,"비공개",List.of("AAPL","NVDA"),LocalDate.of(2026,11,2));em.clear();
        var any=browse.search(owner.getId(),f("",List.of("aapl","NVDA"),NoteBrowseFilter.TickerMatch.ANY),0,1);
        assertThat(any.getTotalElements()).isEqualTo(2);assertThat(any.getContent().get(0).getId()).isEqualTo(both.getId());
        var all=browse.search(owner.getId(),f("",List.of("AAPL","NVDA"),NoteBrowseFilter.TickerMatch.ALL),0,20);
        assertThat(all.getContent()).extracting(x->x.getId()).containsExactly(both.getId());
        assertThat(browse.calendar(owner.getId(),YearMonth.of(2026,11),f("",List.of("AAPL","NVDA"),NoteBrowseFilter.TickerMatch.ANY)).totalNotes()).isEqualTo(2);
    }
    @Test void calendarCountsEveryMatchingNoteBeyondTheCurrentPageAndIntersectsDateFilters() {
        var owner=em.persist(TestFixtures.user());
        for(int i=0;i<8;i++)note(owner,"검토 "+i,List.of("NVDA"),LocalDate.of(2026,11,4));
        note(owner,"지난 달",List.of("NVDA"),LocalDate.of(2026,10,31));
        var filter=f("금리",List.of("NVDA"),NoteBrowseFilter.TickerMatch.ANY);
        assertThat(browse.search(owner.getId(),filter,0,5).getContent()).hasSize(5);
        var month=browse.calendar(owner.getId(),YearMonth.of(2026,11),filter);
        assertThat(month.totalNotes()).isEqualTo(8);assertThat(month.days()).containsExactly(new NoteBrowseService.CalendarDay(LocalDate.of(2026,11,4),8));
        assertThat(browse.calendar(owner.getId(),YearMonth.of(2026,11),new NoteBrowseFilter("",LocalDate.of(2026,11,5),null,List.of(),null)).totalNotes()).isZero();
    }
    @Test void titleBodyAndWildcardCharactersAreLiteralAndOldCreatedDayIsNotReplacedByToday() {
        var owner=em.persist(TestFixtures.user());
        var legacy=note(owner,"증가 10% _",List.of(),null);
        var other=note(owner,"증가 100",List.of(),LocalDate.of(2026,11,1));
        assertThat(browse.search(owner.getId(),f("10% _",List.of(),null),0,5).getContent()).extracting(x->x.getId()).containsExactly(legacy.getId());
        assertThat(browse.search(owner.getId(),f("실적",List.of(),null),0,5).getTotalElements()).isEqualTo(2);
        var month=browse.calendar(owner.getId(),YearMonth.of(2026,11),f("",List.of(),null));
        assertThat(month.days()).anySatisfy(d->{assertThat(d.date()).isEqualTo(LocalDate.of(2026,11,3));assertThat(d.count()).isEqualTo(1);});
    }
    @Test void changingRecordDayMovesCalendarWithoutChangingCreatedTime() {
        var owner=em.persist(TestFixtures.user());var n=note(owner,"날짜 이동",List.of("NVDA"),LocalDate.of(2026,11,3));
        var created=n.getCreatedAt();n.changeNoteDate(LocalDate.of(2026,10,31));em.flush();
        assertThat(n.getCreatedAt()).isEqualTo(created);
        assertThat(browse.calendar(owner.getId(),YearMonth.of(2026,11),f("",List.of(),null)).totalNotes()).isZero();
        assertThat(browse.calendar(owner.getId(),YearMonth.of(2026,10),f("",List.of(),null)).totalNotes()).isEqualTo(1);
    }
    @Test void invalidFiltersAreRejectedBeforeQuerying() {
        assertThatThrownBy(()->new NoteBrowseFilter("a".repeat(201),null,null,List.of(),null)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThatThrownBy(()->new NoteBrowseFilter("",LocalDate.of(2026,11,3),LocalDate.of(2026,11,2),List.of(),null)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThatThrownBy(()->f("",java.util.Collections.nCopies(21,"NVDA"),null)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThatThrownBy(()->browse.search(1L,f("",List.of(),null),0,21)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }
}
