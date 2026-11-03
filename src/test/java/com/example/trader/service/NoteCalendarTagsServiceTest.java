package com.example.trader.service;
import com.example.trader.support.fixtures.TestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static com.example.trader.service.NoteCalendarPeriod.Unit.*;
@DataJpaTest(properties={"spring.jpa.hibernate.ddl-auto=create-drop","spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"})
@Import({NoteBrowseService.class,NoteCalendarService.class})
class NoteCalendarTagsServiceTest {
    @Autowired TestEntityManager em;
    @Autowired NoteCalendarService calendar;
    final NoteBrowseFilter empty=new NoteBrowseFilter("",null,null,List.of(),null);
    @Test void tagWritesAndFinancialWritesPreserveEachOthersFieldsAndVersion(){
        var user=em.persist(TestFixtures.user());
        var money=calendar.save(user.getId(),new NoteCalendarService.SaveRequest(MONTH,"2021-01","12345.67","1.25","USD",null));
        var tagged=calendar.saveTags(user.getId(),new NoteCalendarService.TagSaveRequest(MONTH,"2021-01",List.of("#반도체","방산","의학","물타기"),money.version()));
        assertThat(tagged.amount()).isEqualTo("12345.67");assertThat(tagged.rate()).isEqualTo("1.25");assertThat(tagged.currency()).isEqualTo("USD");
        assertThat(tagged.tags()).containsExactly("반도체","방산","의학","물타기");assertThat(tagged.version()).isGreaterThan(money.version());
        var updated=calendar.save(user.getId(),new NoteCalendarService.SaveRequest(MONTH,"2021-01","-200","-2.5","KRW",tagged.version()));
        assertThat(updated.tags()).isEqualTo(tagged.tags());
        var cleared=calendar.saveTags(user.getId(),new NoteCalendarService.TagSaveRequest(MONTH,"2021-01",List.of(),updated.version()));
        assertThat(cleared.tags()).isEmpty();assertThat(cleared.amount()).isEqualTo("-200");assertThat(cleared.rate()).isEqualTo("-2.5");
        assertThatThrownBy(()->calendar.saveTags(user.getId(),new NoteCalendarService.TagSaveRequest(MONTH,"2021-01",List.of("다른값"),updated.version())))
            .isInstanceOfSatisfying(ResponseStatusException.class,e->assertThat(e.getStatusCode().value()).isEqualTo(409));
    }
    @Test void tagOnlyRecordsPersistInOrderRemainPrivateAndDoNotLeakAcrossUnitsOrNoteFilters(){
        var owner=em.persist(TestFixtures.user());var other=em.persist(TestFixtures.user());
        var saved=calendar.saveTags(owner.getId(),new NoteCalendarService.TagSaveRequest(MONTH,"2021-02",List.of("#반도체","방산","의학","물타기","AI","ai"),null));
        assertThat(saved.amount()).isNull();assertThat(saved.rate()).isNull();
        em.clear();
        var filter=new NoteBrowseFilter("no matching note",null,null,List.of("NVDA"),null);
        assertThat(calendar.window(owner.getId(),MONTH,"2021",filter).periods().get(1).performance().tags()).containsExactly("반도체","방산","의학","물타기","AI");
        assertThat(calendar.window(other.getId(),MONTH,"2021",empty).periods()).allSatisfy(c->assertThat(c.performance()).isNull());
        assertThat(calendar.window(owner.getId(),YEAR,"2021",empty).periods()).allSatisfy(c->assertThat(c.performance()).isNull());
        assertThat(calendar.window(owner.getId(),DAY,"2021-02",empty).periods()).allSatisfy(c->assertThat(c.performance()).isNull());
    }
    @Test void invalidTagsAreRejectedAndUnicodeDuplicatesNormalizeConsistently(){
        assertThat(NoteCalendarTags.normalize(List.of(" ＡＩ ","AI","#반도체","𠮷산업"))).containsExactly("AI","반도체","𠮷산업");
        for(var raw:List.of(List.of(""),List.of("공백 태그"),List.of("<script>"),List.of("가".repeat(21)),java.util.stream.IntStream.range(0,13).mapToObj(i->"태그"+i).toList()))
            assertThatThrownBy(()->NoteCalendarTags.normalize(raw)).isInstanceOf(ResponseStatusException.class);
        var owner=em.persist(TestFixtures.user());
        assertThatThrownBy(()->calendar.saveTags(owner.getId(),new NoteCalendarService.TagSaveRequest(MONTH,"2021-01",null,null))).isInstanceOf(ResponseStatusException.class);
    }
    @Test void deletingAnOwnerCascadesTheirPeriodTagsAndPreservesOtherOwnersRecords(){
        var owner=em.persist(TestFixtures.user());var other=em.persist(TestFixtures.user());
        calendar.saveTags(owner.getId(),new NoteCalendarService.TagSaveRequest(YEAR,"2021",List.of("반도체"),null));
        calendar.saveTags(other.getId(),new NoteCalendarService.TagSaveRequest(YEAR,"2021",List.of("방산"),null));
        em.flush();em.clear();
        em.getEntityManager().createNativeQuery("delete from users where id=:id").setParameter("id",owner.getId()).executeUpdate();em.clear();
        assertThat(((Number)em.getEntityManager().createNativeQuery("select count(*) from note_calendar_tag").getSingleResult()).intValue()).isEqualTo(1);
        assertThat(calendar.window(other.getId(),YEAR,"2021",empty).periods().get(0).performance().tags()).containsExactly("방산");
    }
}
