package com.example.trader.controller;

import com.example.trader.security.service.SecurityService;
import com.example.trader.service.NoteBrowseFilter;
import com.example.trader.service.NoteBrowseService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/note")
public class NoteBrowseController {
    private final NoteBrowseService browse;

    @GetMapping("/search")
    public Object search(@RequestParam(defaultValue="") String q,
                         @RequestParam(required=false) LocalDate start,
                         @RequestParam(required=false) LocalDate end,
                         @RequestParam(required=false) List<String> tickers,
                         @RequestParam(defaultValue="ANY") NoteBrowseFilter.TickerMatch tickerMatch,
                         @RequestParam(defaultValue="0") int page,
                         @RequestParam(defaultValue="5") int size) {
        return browse.search(SecurityService.getAuthenticationUserId(),new NoteBrowseFilter(q,start,end,tickers,tickerMatch),page,size);
    }

    @GetMapping("/calendar")
    public Object calendar(@RequestParam String month,
                           @RequestParam(defaultValue="") String q,
                           @RequestParam(required=false) LocalDate start,
                           @RequestParam(required=false) LocalDate end,
                           @RequestParam(required=false) List<String> tickers,
                           @RequestParam(defaultValue="ANY") NoteBrowseFilter.TickerMatch tickerMatch) {
        YearMonth parsed;
        try {
            if (!month.matches("\\d{4}-\\d{2}")) throw new DateTimeParseException("invalid",month,0);
            parsed=YearMonth.parse(month);
        } catch (DateTimeParseException ex) { throw NoteBrowseFilter.bad("월을 YYYY-MM 형식으로 지정해주세요."); }
        return browse.calendar(SecurityService.getAuthenticationUserId(),parsed,new NoteBrowseFilter(q,start,end,tickers,tickerMatch));
    }
}
