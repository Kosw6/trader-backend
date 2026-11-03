package com.example.trader.repository;

import com.example.trader.entity.Note;
import com.example.trader.entity.NoteTickers;
import com.example.trader.support.fixtures.TestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

@DataJpaTest(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class NoteTickersTest {
    @Autowired TestEntityManager em;
    @Autowired NoteRepository repository;

    @Test void persistsReloadsFiltersEveryTickerWithoutDuplicatesOrOtherUsers() {
        var owner = em.persist(TestFixtures.user());
        var other = em.persist(TestFixtures.user());
        var note = TestFixtures.note(owner, null);
        note.changeStockSymbs(List.of("AAPL", "NVDA", "NVDA", "BRK.B"));
        repository.saveAndFlush(note);
        var unrelated = TestFixtures.note(other, null);
        unrelated.changeStockSymbs(List.of("NVDA"));
        repository.saveAndFlush(unrelated);
        em.clear();
        assertThat(repository.findById(note.getId()).orElseThrow().getStockSymbs()).containsExactly("AAPL", "NVDA", "BRK.B");
        for (var ticker : List.of("AAPL", "NVDA", "BRK.B")) {
            var page = repository.findByUserIdAndStockSymb(owner.getId(), ticker, PageRequest.of(0, 1));
            assertThat(page.getTotalElements()).isEqualTo(1);
            assertThat(page.getContent().get(0).getId()).isEqualTo(note.getId());
            assertThat(repository.findByUserIdAndStockSymbAndNoteDateBetween(owner.getId(), ticker,
                    LocalDate.now(), LocalDate.now(), PageRequest.of(0, 5)).getTotalElements()).isEqualTo(1);
        }
        assertThat(repository.findByUserIdAndStockSymbAndNoteDateBetween(owner.getId(), "NVDA",
                LocalDate.now().minusDays(2), LocalDate.now().minusDays(1), PageRequest.of(0, 5))).isEmpty();
    }

    @Test void legacyRowsRemainReadableAndExplicitEmptyClearsAllTags() {
        var owner = em.persist(TestFixtures.user());
        var note = repository.saveAndFlush(TestFixtures.note(owner, null));
        em.clear();
        note = repository.findById(note.getId()).orElseThrow();
        assertThat(note.getStockSymbs()).containsExactly("AAPL");
        note.applyTickerRequest(List.of("AAPL", "NVDA"), "AAPL");
        repository.flush(); em.clear();
        note = repository.findById(note.getId()).orElseThrow();
        note.applyTickerRequest(List.of(), "AAPL");
        repository.flush(); em.clear();
        note = repository.findById(note.getId()).orElseThrow();
        assertThat(note.getStockSymbs()).isEmpty();
        assertThat(note.getStockSymb()).isNull();
        assertThat(repository.findByUserIdAndStockSymb(owner.getId(), "NVDA", PageRequest.of(0, 5))).isEmpty();
    }

    @Test void oldClientTextEditsPreserveSecondaryTagsAndCannotSilentlyReplaceThem() {
        var note = TestFixtures.note(TestFixtures.user(), null);
        note.changeStockSymbs(List.of("AAPL", "NVDA"));
        note.applyTickerRequest(null, "AAPL");
        assertThat(note.getStockSymbs()).containsExactly("AAPL", "NVDA");
        assertThatThrownBy(() -> note.applyTickerRequest(null, "MSFT")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void rejectsInvalidValuesAndTooManyTagsButKeepsPunctuation() {
        assertThat(NoteTickers.normalize(List.of("BRK.B", "BTC-USD", "005930", "BRK.B"))).containsExactly("BRK.B", "BTC-USD", "005930");
        for (var value : List.of("", " ", "AAPL,NVDA", "AA PL", "x".repeat(65)))
            assertThatThrownBy(() -> NoteTickers.normalize(List.of(value))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> NoteTickers.normalize(Collections.nCopies(21, "AAPL"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> NoteTickers.normalize(Collections.singletonList(null))).isInstanceOf(IllegalArgumentException.class);
    }
}
