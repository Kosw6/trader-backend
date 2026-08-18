package com.example.trader.service;

import com.example.trader.dto.TickerDto;
import com.example.trader.repository.TickerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TickerServiceTest {

    @Mock
    TickerRepository repository;

    @Test
    void stock_info_search_result_is_mapped_to_asset_suggestion() {
        when(repository.suggestFuzzy("apple", 10)).thenReturn(List.<Object[]>of(new Object[]{
                42L, "AAPL", "애플", "Apple Inc.", "NASDAQ", "USD", "EQUITY", 1, 0.9d
        }));
        TickerService service = new TickerService(repository);

        List<TickerDto> result = service.suggestTicker(" apple ");

        assertThat(result).containsExactly(new TickerDto(
                42L, "AAPL", "애플", "Apple Inc.", "NASDAQ", "USD", "EQUITY"
        ));
        verify(repository).suggestFuzzy("apple", 10);
    }

    @Test
    void short_query_does_not_touch_database() {
        TickerService service = new TickerService(repository);

        assertThat(service.suggestTicker("A")).isEmpty();
    }
}
