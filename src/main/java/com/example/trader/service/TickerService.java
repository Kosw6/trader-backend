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
    public List<TickerDto> suggestTicker(String query) {
        String normalized = query == null ? "" : query.trim();
        if (normalized.length() < 2) {
            return List.of();
        }

        var rows = repository.suggestFuzzy(normalized, 10);
        return rows.stream().map(row -> new TickerDto(
                ((Number) row[0]).longValue(),
                (String) row[1],
                (String) row[2],
                (String) row[3],
                (String) row[4],
                (String) row[5],
                (String) row[6]
        )).toList();
    }
}
