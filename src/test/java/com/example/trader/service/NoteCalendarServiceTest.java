package com.example.trader.service;

import com.example.trader.entity.Note;
import com.example.trader.support.fixtures.TestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static com.example.trader.service.NoteCalendarPeriod.Unit.*;

@DataJpaTest(properties={"spring.jpa.hibernate.ddl-auto=create-drop","spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"})
@Import({NoteBrowseService.class,NoteCalendarService.class})
class NoteCalendarServiceTest {
    @Autowired TestEntityManager em;
    @Autowired NoteCalendarService calendar;
    final NoteBrowseFilter empty=new NoteBrowseFilter("",null,null,List.of(),null);
    NoteCalendarService.SaveRequest request(NoteCalendarPeriod.Unit unit,String key,String amount,String rate,String currency,Long version){
        return new NoteCalendarService.SaveRequest(unit,key,amount,rate,currency,version);
    }
    void note(com.example.trader.entity.User user,String day,String ticker){
        var n=TestFixtures.note(user,null);n.changeNoteDate(LocalDate.parse(day));n.changeStockSymbs(List.of(ticker));em.persistAndFlush(n);
    }
    @Test void yearsMonthsAndLeapDaysHaveCompleteOwnerScopedCountsAndStableEmptyCells(){
        var a=em.persist(TestFixtures.user());var b=em.persist(TestFixtures.user());
        note(a,"2021-01-03","NVDA");note(a,"2022-02-28","AAPL");note(a,"2024-02-29","NVDA");note(b,"2021-01-03","NVDA");
        var years=calendar.window(a.getId(),YEAR,"2021",empty);
        assertThat(years.periods()).hasSize(10);assertThat(years.totalNotes()).isEqualTo(3);
        assertThat(years.periods().get(0).count()).isEqualTo(1);assertThat(years.periods().get(1).count()).isEqualTo(1);
        assertThat(calendar.window(a.getId(),MONTH,"2024",empty).periods().get(1).count()).isEqualTo(1);
        assertThat(calendar.window(a.getId(),DAY,"2024-02",empty).periods()).hasSize(29);
        assertThat(calendar.window(a.getId(),DAY,"2024-02",empty).periods().get(28).count()).isEqualTo(1);
        assertThat(calendar.window(a.getId(),YEAR,"9999",empty).periods()).hasSize(1);
    }
    @Test void amountAndRateStayExactSeparateAcrossUnitsAndPrivateAcrossOwners(){
        var a=em.persist(TestFixtures.user());var b=em.persist(TestFixtures.user());
        var saved=calendar.save(a.getId(),request(MONTH,"2021-02","-123456789.25","-2.1250","USD",null));
        assertThat(saved.amount()).isEqualTo("-123456789.25");assertThat(saved.rate()).isEqualTo("-2.1250");
        calendar.save(a.getId(),request(YEAR,"2021","10000","5","KRW",null));
        calendar.save(a.getId(),request(DAY,"2021-02-01","0",null,"KRW",null));
        em.clear();
        assertThat(calendar.window(a.getId(),MONTH,"2021",empty).periods().get(1).performance().amount()).isEqualTo("-123456789.25");
        assertThat(calendar.window(a.getId(),YEAR,"2021",empty).periods().get(0).performance().amount()).isEqualTo("10000.00");
        assertThat(calendar.window(b.getId(),MONTH,"2021",empty).periods()).allSatisfy(c->assertThat(c.performance()).isNull());
    }
    @Test void noteFiltersChangeCountsWithoutChangingWholePeriodPerformance(){
        var a=em.persist(TestFixtures.user());note(a,"2021-01-03","NVDA");note(a,"2021-01-04","AAPL");
        calendar.save(a.getId(),request(MONTH,"2021-01","50000","3.25","KRW",null));
        var filter=new NoteBrowseFilter("",LocalDate.of(2021,1,3),LocalDate.of(2021,1,3),List.of("NVDA"),null);
        var cell=calendar.window(a.getId(),MONTH,"2021",filter).periods().get(0);
        assertThat(cell.count()).isEqualTo(1);assertThat(cell.performance().amount()).isEqualTo("50000");
    }
    @Test void updatesUseVersionAndBlankClearsOnlyThatPeriodsManualFields(){
        var a=em.persist(TestFixtures.user());
        var first=calendar.save(a.getId(),request(YEAR,"2021","100","1","KRW",null));
        assertThatThrownBy(()->calendar.save(a.getId(),request(YEAR,"2021","200","2","KRW",null)))
            .isInstanceOfSatisfying(ResponseStatusException.class,e->assertThat(e.getStatusCode().value()).isEqualTo(409));
        var second=calendar.save(a.getId(),request(YEAR,"2021","-100",null,"USD",first.version()));
        assertThat(second.version()).isGreaterThan(first.version());
        assertThatThrownBy(()->calendar.save(a.getId(),request(YEAR,"2021","300","3","KRW",first.version())))
            .isInstanceOfSatisfying(ResponseStatusException.class,e->assertThat(e.getStatusCode().value()).isEqualTo(409));
        var cleared=calendar.save(a.getId(),request(YEAR,"2021","","","KRW",second.version()));
        assertThat(cleared.amount()).isNull();assertThat(cleared.rate()).isNull();
    }
    @Test void invalidNumbersCurrenciesAndNoncanonicalPeriodsFailBeforePersist(){
        var a=em.persist(TestFixtures.user());
        for(String amount:List.of("NaN","1e6","1,000","1.001","1000000000000"))
            assertThatThrownBy(()->calendar.save(a.getId(),request(YEAR,"2021",amount,null,"KRW",null))).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->calendar.save(a.getId(),request(YEAR,"2021","1","1.12345","KRW",null))).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->calendar.save(a.getId(),request(YEAR,"2021","1","1","EUR",null))).isInstanceOf(ResponseStatusException.class);
        for(String key:List.of("0000","2021-01","abcd"))assertThatThrownBy(()->NoteCalendarPeriod.parse(YEAR,key)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->NoteCalendarPeriod.parse(DAY,"2021-02-29")).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->NoteCalendarPeriod.parse(MONTH,"2021-13")).isInstanceOf(ResponseStatusException.class);
        assertThat(calendar.window(a.getId(),YEAR,"2021",empty).periods()).allSatisfy(c->assertThat(c.performance()).isNull());
    }
}
