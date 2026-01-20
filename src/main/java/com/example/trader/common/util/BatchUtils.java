package com.example.trader.common.util;
import java.util.ArrayList;
import java.util.List;

public final class BatchUtils {
    private BatchUtils() {}
    //리스트를 여러개의 리스트로 쪼개는 함수
    public static <T> List<List<T>> batches(List<T> list, int batchSize) {
        if (batchSize <= 0) throw new IllegalArgumentException("batchSize must be > 0");
        List<List<T>> out = new ArrayList<>();
        for (int i = 0; i < list.size(); i += batchSize) {
            out.add(list.subList(i, Math.min(i + batchSize, list.size())));
        }
        return out;
    }
}
