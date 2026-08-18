package com.example.trader.service;

import com.example.trader.dto.PriceBarDto;
import com.example.trader.repository.PriceBarRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssetPriceServiceTest {

    @Mock
    PriceBarRepository repository;

    @Test
    void reads_only_price_bar_by_asset_id_with_normalized_dimensions() {
        AssetPriceService service = new AssetPriceService(repository);
        OffsetDateTime before = OffsetDateTime.parse("2026-08-18T00:00:00Z");
        when(repository.findBefore(eq(42L), eq(before), eq("KIS"), eq("1D"), eq(500)))
                .thenReturn(List.of());

        List<PriceBarDto> result = service.getBefore(42L, before, 999, "kis", "1d");

        assertThat(result).isEmpty();
        verify(repository).findBefore(42L, before, "KIS", "1D", 500);
    }
}
