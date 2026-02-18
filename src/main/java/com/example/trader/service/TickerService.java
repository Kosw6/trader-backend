package com.example.trader.service;

import com.example.trader.dto.TickerDto;
import com.example.trader.repository.TickerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TickerService {
    private final TickerRepository repository;
    @Transactional(readOnly = true)
    public List suggestTicker(String ticker){
        var rows = repository.suggestFuzzy(ticker, 10);
        List<TickerDto> tickerDtos = rows.stream().map(r -> new TickerDto(
                (String) r[0],                     // symbol
                (String) r[1],                     // name_ko
                (String) r[2]                     // name_en
        )).toList();
        return tickerDtos;
    }
}
