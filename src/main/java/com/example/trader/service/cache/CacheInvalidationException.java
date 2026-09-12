package com.example.trader.service.cache;

import java.util.List;

public class CacheInvalidationException extends RuntimeException {

    private final List<String> failedCaches;

    public CacheInvalidationException(List<String> failedCaches, Throwable cause) {
        super("캐시 무효화에 실패했습니다: " + String.join(", ", failedCaches), cause);
        this.failedCaches = List.copyOf(failedCaches);
    }

    public List<String> getFailedCaches() {
        return failedCaches;
    }
}
