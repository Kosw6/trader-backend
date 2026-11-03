package com.example.trader.indicator;

import com.example.trader.dto.PriceBarDto;
import com.example.trader.repository.PriceBarRepository;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class IndicatorServiceTest {
    private final PriceBarRepository repo=mock(PriceBarRepository.class);
    private final IndicatorService service=new IndicatorService(repo,
            Clock.fixed(Instant.parse("2026-10-17T12:00:00Z"),ZoneOffset.UTC));
    private PriceBarDto bar(int day) {
        return new PriceBarDto(1L,OffsetDateTime.parse("2026-01-01T00:00:00Z").plusDays(day),
                null,null,null,BigDecimal.valueOf(100+day),null,null,"USD","KIS","1D");
    }
    @Test void displayCountDoesNotChangeSeedOrLatestValues() {
        var bars=new ArrayList<PriceBarDto>(); for(int i=0;i<200;i++) bars.add(bar(i));
        when(repo.findBefore(anyLong(),any(),eq("KIS"),eq("1D"),eq(750))).thenReturn(bars);
        var chart=service.read(1,null,100); var ai=service.read(1,null,1);
        assertEquals(chart.points().get(99),ai.points().get(0));
        assertEquals(LocalDate.parse("2026-10-16"),ai.requestedAsOf());
        assertEquals("UNADJUSTED_CLOSE",ai.priceBasis());
    }
    @Test void adjustedChartIndicatorsUseOnlyTheSameSnapshot() {
        var bars=new ArrayList<PriceBarDto>(); for(int i=0;i<200;i++) bars.add(bar(i));
        when(repo.findSnapshotBefore(eq(1L),any(),eq("KIS"),eq("1D"),eq(750),eq(7L))).thenReturn(bars);
        var result=service.read(1,null,100,7L);
        assertEquals("KIS_PROVIDER_ADJUSTED",result.priceBasis());
        assertEquals("SUCCESS",result.status());
        verify(repo,never()).findBefore(anyLong(),any(),anyString(),anyString(),anyInt());
        assertThrows(IllegalArgumentException.class,()->service.read(1,null,100,0L));
    }
    @Test void emptyDataAndInvalidRequestAreDistinct() {
        when(repo.findBefore(anyLong(),any(),anyString(),anyString(),anyInt())).thenReturn(List.of());
        assertEquals("EMPTY",service.read(1,null,100).status());
        assertThrows(IllegalArgumentException.class,()->service.read(1,LocalDate.parse("2026-10-17"),100));
        assertThrows(IllegalArgumentException.class,()->service.read(1,null,501));
        assertThrows(IllegalArgumentException.class,()->service.read(0,null,100));
    }
    @Test void duplicateDatesAndWrongSourceAreNotSilentlyDropped() {
        when(repo.findBefore(anyLong(),any(),anyString(),anyString(),anyInt())).thenReturn(List.of(bar(0),bar(0)));
        assertThrows(IllegalArgumentException.class,()->service.read(1,null,100));
    }
    @Test void insufficientHistoryKeepsNullValuesAndPartialStatus() {
        when(repo.findBefore(anyLong(),any(),anyString(),anyString(),anyInt()))
                .thenReturn(List.of(bar(0),bar(1)));
        var response=service.read(1,null,100);
        assertEquals("PARTIAL",response.status());
        assertNull(response.points().get(1).rsi());
        assertNull(response.points().get(1).macd());
    }
    @Test void wrongAssetOrSourceCannotBePresentedAsRequestedIndicator() {
        var original=bar(0);
        for (var invalid:List.of(
                new PriceBarDto(2L,original.timestamp(),null,null,null,BigDecimal.ONE,null,null,"USD","KIS","1D"),
                new PriceBarDto(1L,original.timestamp(),null,null,null,BigDecimal.ONE,null,null,"USD","OTHER","1D"),
                new PriceBarDto(1L,null,null,null,null,BigDecimal.ONE,null,null,"USD","KIS","1D"))) {
            when(repo.findBefore(anyLong(),any(),anyString(),anyString(),anyInt())).thenReturn(List.of(invalid));
            assertThrows(IllegalArgumentException.class,()->service.read(1,null,100));
        }
    }
}
