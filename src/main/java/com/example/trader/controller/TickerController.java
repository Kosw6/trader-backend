package com.example.trader.controller;

import com.example.trader.dto.TickerDto;
import com.example.trader.service.TickerService;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
@RequiredArgsConstructor
@RequestMapping("/api/suggest")
public class TickerController {
    private final TickerService tickerService;

    @GetMapping(params = {"q"})
    public List<TickerDto> suggestTicker(@Parameter(name = "ticker", example = "TSLA") @RequestParam String q){
        if (q.length()<2) return List.of();
        return tickerService.suggestTicker(q);
    }
}
