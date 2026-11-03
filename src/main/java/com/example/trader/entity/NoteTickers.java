package com.example.trader.entity;

import java.util.LinkedHashSet;
import java.util.List;

/** Ordered ticker tags, not a comma-separated string. Does not infer an exchange. */
public final class NoteTickers {
    public static final int MAX_TAGS = 20;
    private NoteTickers() {}

    public static List<String> normalize(List<String> symbols) {
        if (symbols == null || symbols.size() > MAX_TAGS)
            throw new IllegalArgumentException("티커는 최대 20개까지 연결할 수 있습니다.");
        var unique = new LinkedHashSet<String>();
        for (String value : symbols) {
            if (value == null || !value.trim().matches("[^\\s\\p{Cntrl},]{1,64}"))
                throw new IllegalArgumentException("올바른 티커를 각각 선택해주세요.");
            unique.add(value.trim());
        }
        return List.copyOf(unique);
    }
}
