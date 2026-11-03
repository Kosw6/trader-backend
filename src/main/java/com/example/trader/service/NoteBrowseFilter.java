package com.example.trader.service;

import com.example.trader.entity.NoteTickers;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/** One owner-scoped filter contract for both note list and calendar counts. */
public record NoteBrowseFilter(String query, LocalDate start, LocalDate end,
                               List<String> tickers, TickerMatch tickerMatch) {
    public enum TickerMatch { ANY, ALL }
    public NoteBrowseFilter {
        query = query == null ? "" : query.trim();
        if (query.length() > 200 || query.chars().anyMatch(c -> Character.isISOControl(c)))
            throw bad("검색어는 제어문자 없이 200자 이내로 입력해주세요.");
        if (start != null && end != null && start.isAfter(end))
            throw bad("시작일은 종료일보다 늦을 수 없습니다.");
        if ((start != null && (start.getYear() < 1 || start.getYear() > 9999))
                || (end != null && (end.getYear() < 1 || end.getYear() > 9999)))
            throw bad("날짜를 확인해주세요.");
        try {
            tickers = NoteTickers.normalize(tickers == null ? List.of() : tickers).stream()
                    .map(s -> s.toUpperCase(Locale.ROOT)).distinct().toList();
        } catch (IllegalArgumentException ex) { throw bad(ex.getMessage()); }
        tickerMatch = tickerMatch == null ? TickerMatch.ANY : tickerMatch;
    }
    public static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
    public static String literalPattern(String query) {
        return "%" + query.toLowerCase(Locale.ROOT).replace("\\", "\\\\")
                .replace("%", "\\%").replace("_", "\\_") + "%";
    }
}
