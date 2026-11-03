package com.example.trader.indicator;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record IndicatorResponse(String status, long assetId, String source, String interval,
        String priceBasis, String calculationVersion, LocalDate requestedAsOf, LocalDate asOf,
        int inputBars, int warmupBars, Map<String, Integer> parameters,
        List<Point> points, List<String> warnings) {
    public record Point(LocalDate date, double close, Double rsi, Double macd,
                        Double signal, Double histogram) {}
}
