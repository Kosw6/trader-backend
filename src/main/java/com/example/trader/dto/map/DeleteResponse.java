package com.example.trader.dto.map;

import java.util.List;

public record DeleteResponse(
        DeletedIds deleted
) {
    public static DeleteResponse of(List<Long> directoryIds, List<Long> pageIds) {
        return new DeleteResponse(new DeletedIds(directoryIds, pageIds));
    }
    public static DeleteResponse empty() {
        return new DeleteResponse(null); // ← 바디 생략
    }

    public record DeletedIds(
            List<Long> directories,
            List<Long> pages
    ) {}
}
