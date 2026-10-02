package com.example.trader.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public record AiReadLimits(int sourceBytes, int defaultPage, int maxPage, int relationSetItems) {
    public AiReadLimits(@Value("${AI_SOURCE_RESPONSE_MAX_BYTES:1048576}") int sourceBytes,
                        @Value("${AI_RELATION_PAGE_DEFAULT_SIZE:30}") int defaultPage,
                        @Value("${AI_RELATION_PAGE_MAX_SIZE:100}") int maxPage,
                        @Value("${AI_RELATION_SET_MAX_ITEMS:10000}") int relationSetItems) {
        if(sourceBytes<1024 || sourceBytes>10485760 || defaultPage<1 || maxPage<defaultPage
                || maxPage>1000 || relationSetItems<maxPage || relationSetItems>100000)
            throw new IllegalArgumentException("AI 조회 제한 설정이 올바르지 않습니다.");
        this.sourceBytes=sourceBytes;this.defaultPage=defaultPage;this.maxPage=maxPage;this.relationSetItems=relationSetItems;
    }
}
