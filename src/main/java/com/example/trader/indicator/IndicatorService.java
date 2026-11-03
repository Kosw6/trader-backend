package com.example.trader.indicator;

import com.example.trader.dto.PriceBarDto;
import com.example.trader.repository.PriceBarRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

/** 지표 산식은 한 곳에서 관리한다. 수정가격 세대는 명시적으로 전달하며 GPU에는 의존하지 않는다. */
@Service
public class IndicatorService {
    private final PriceBarRepository repository;
    private final Clock clock;
    @org.springframework.beans.factory.annotation.Autowired
    public IndicatorService(PriceBarRepository repository) { this(repository, Clock.systemUTC()); }
    IndicatorService(PriceBarRepository repository, Clock clock) { this.repository=repository; this.clock=clock; }

    @Transactional(readOnly=true)
    public IndicatorResponse read(long assetId, LocalDate asOf, int count) {
        return read(assetId,asOf,count,null);
    }

    @Transactional(readOnly=true)
    public IndicatorResponse read(long assetId, LocalDate asOf, int count, Long snapshotId) {
        LocalDate today = LocalDate.now(clock);
        LocalDate end = asOf == null ? today.minusDays(1) : asOf;
        if (assetId <= 0 || count < 1 || count > 500 || !end.isBefore(today)) {
            throw new IllegalArgumentException("종목 ID, 봉 수(1~500), UTC 전일까지의 기준일을 확인해주세요.");
        }
        var before = end.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC).minusNanos(1000);
        // 표시 개수가 달라도 같은 기준일은 같은 시드를 쓴다(차트와 챗봇의 EMA 값 일치).
        if(snapshotId!=null && snapshotId<=0) throw new IllegalArgumentException("유효한 가격 세대가 필요합니다.");
        var bars = new ArrayList<>(snapshotId==null ? repository.findBefore(assetId,before,"KIS","1D",750)
                : repository.findSnapshotBefore(assetId,before,"KIS","1D",750,snapshotId));
        Set<LocalDate> days = new HashSet<>();
        for (var b : bars) {
            if (b.timestamp()==null || b.close()==null || b.assetId()==null || b.assetId()!=assetId
                    || !"KIS".equals(b.source()) || !"1D".equals(b.interval())
                    || b.timestamp().withOffsetSameInstant(ZoneOffset.UTC).toLocalDate().isAfter(end)
                    || !days.add(b.timestamp().withOffsetSameInstant(ZoneOffset.UTC).toLocalDate())) {
                throw new IllegalArgumentException("일봉 원본의 날짜·종목·가격 기준이 일치하지 않습니다.");
            }
        }
        bars.sort(Comparator.comparing(PriceBarDto::timestamp));
        var values = IndicatorCalculator.calculate(bars.stream().mapToDouble(b -> b.close().doubleValue()).toArray());
        int start = Math.max(0,bars.size()-count);
        List<IndicatorResponse.Point> points = new ArrayList<>();
        for (int i=start;i<bars.size();i++) {
            var b=bars.get(i); var v=values[i];
            points.add(new IndicatorResponse.Point(b.timestamp().withOffsetSameInstant(ZoneOffset.UTC).toLocalDate(),
                    b.close().doubleValue(),v.rsi(),v.macd(),v.signal(),v.histogram()));
        }
        var warnings = new ArrayList<>(List.of(snapshotId==null ? "KIS 저장 일봉 종가 기준이며 수정주가가 아닙니다. 분할·병합에 영향을 받을 수 있습니다."
                : "KIS 공급자 수정 일봉 종가, 검토본 #"+snapshotId+" 기준입니다. 분할 전용/배당 재투자 총수익률을 뜻하지 않습니다.",
                "UTC 전일까지의 저장 봉만 사용합니다. 실시간 지표나 매매 추천이 아닙니다."));
        if (start<250) warnings.add("초기화용 과거 봉이 250개보다 적어 다른 서비스의 지표 값과 차이가 날 수 있습니다.");
        boolean complete = !points.isEmpty() && points.get(points.size()-1).rsi()!=null
                && points.get(points.size()-1).signal()!=null;
        if (!complete) warnings.add("지표 계산에 필요한 과거 봉이 부족합니다. 없는 값은 0으로 채우지 않습니다.");
        return new IndicatorResponse(points.isEmpty()?"EMPTY":complete?"SUCCESS":"PARTIAL",assetId,"KIS","1D",
                snapshotId==null ? "UNADJUSTED_CLOSE" : "KIS_PROVIDER_ADJUSTED","rsi-wilder14-macd-sma-seed-12-26-9-v1",end,
                points.isEmpty()?null:points.get(points.size()-1).date(),bars.size(),start,
                Map.of("rsi",14,"fast",12,"slow",26,"signal",9),List.copyOf(points),List.copyOf(warnings));
    }
}
