package com.example.trader.controller;

import com.example.trader.dto.PriceBarDto;
import com.example.trader.dto.PriceChartSnapshotDto;
import com.example.trader.service.AssetPriceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;

@Tag(name = "Asset Price API", description = "asset_id 기반 주가 시계열 API")
@RestController
@RequestMapping("/api/assets")
@RequiredArgsConstructor
public class AssetPriceController {

    private final AssetPriceService assetPriceService;

    @GetMapping("/{assetId}/chart-corporate-actions")
    public List<com.example.trader.dto.ChartCorporateActionDto> getCorporateActions(@PathVariable Long assetId) {
        return assetPriceService.getCorporateActions(assetId);
    }

    @GetMapping("/{assetId}/price-chart-snapshots")
    public List<PriceChartSnapshotDto> getSnapshots(@PathVariable Long assetId) {
        return assetPriceService.getSnapshots(assetId);
    }

    @Operation(summary = "기준 시각 이전의 Asset 일·주·월·년봉 조회")
    @GetMapping("/{assetId}/price-bars")
    public ResponseEntity<List<PriceBarDto>> getPriceBarsBefore(
            @PathVariable Long assetId,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            OffsetDateTime before,
            @RequestParam(defaultValue = "100") int count,
            @RequestParam(defaultValue = "KIS") String source,
            @RequestParam(defaultValue = "1D") String interval,
            @RequestParam(required = false) Long snapshotId
    ) {
        return ResponseEntity.ok(
                assetPriceService.getBefore(assetId, before, count, source, interval, snapshotId)
        );
    }
}
