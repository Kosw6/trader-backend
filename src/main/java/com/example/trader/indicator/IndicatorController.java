package com.example.trader.indicator;

import com.example.trader.ai.AiRequestContext;
import com.example.trader.ai.AiApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.Set;

@RestController
public class IndicatorController {
    private final IndicatorService service;
    public IndicatorController(IndicatorService service) { this.service=service; }
    @GetMapping("/api/assets/{assetId}/indicators")
    public ResponseEntity<IndicatorResponse> chart(@PathVariable long assetId,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate asOf,
            @RequestParam(defaultValue="100") int count,
            @RequestParam(required=false) Long snapshotId) {
        return ResponseEntity.ok().header("Cache-Control","no-store").body(service.read(assetId,asOf,count,snapshotId));
    }
    @GetMapping("/internal/ai/retrieval/indicators/{assetId}")
    public ResponseEntity<IndicatorResponse> ai(@PathVariable long assetId,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate asOf,
            @RequestParam(defaultValue="100") int count, HttpServletRequest request) {
        if (!(request.getAttribute(AiRequestContext.ATTRIBUTE) instanceof AiRequestContext context)
                || context.indexing() || context.userId()==null) throw new AiApiException(403,"SERVICE_OPERATION_FORBIDDEN");
        if (!Set.of("asOf","count").containsAll(request.getParameterMap().keySet())) throw AiApiException.invalid();
        return chart(assetId,asOf,count,null);
    }
}
