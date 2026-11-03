package com.example.trader.controller;

import com.example.trader.security.service.SecurityService;
import com.example.trader.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/note/calendar")
public class NoteCalendarController {
    private final NoteCalendarService calendar;
    @GetMapping("/periods")
    public Object periods(@RequestParam NoteCalendarPeriod.Unit unit,@RequestParam String anchor,
            @RequestParam(defaultValue="") String q,@RequestParam(required=false) LocalDate start,
            @RequestParam(required=false) LocalDate end,@RequestParam(required=false) List<String> tickers,
            @RequestParam(defaultValue="ANY") NoteBrowseFilter.TickerMatch tickerMatch) {
        return calendar.window(SecurityService.getAuthenticationUserId(),unit,anchor,new NoteBrowseFilter(q,start,end,tickers,tickerMatch));
    }
    @PutMapping("/performance")
    public Object save(@RequestBody NoteCalendarService.SaveRequest request) {
        return calendar.save(SecurityService.getAuthenticationUserId(),request);
    }
    @PutMapping("/tags")
    public Object saveTags(@RequestBody NoteCalendarService.TagSaveRequest request) {
        return calendar.saveTags(SecurityService.getAuthenticationUserId(),request);
    }
}
