package com.example.trader.service;

import com.example.trader.dto.TickerDto;
import com.example.trader.repository.TickerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TickerService {
    private final TickerRepository repository;

    public List suggestTicker(String ticker){
        var rows = repository.suggestFuzzy(ticker, 10);
        return rows.stream().map(r -> new TickerDto(
                (String)  r[0],                     // symbol
                (String)  r[1],                     // name_ko
                (String)  r[2]                     // name_en
        )).toList();
    }
}
