package com.example.trader.service;

import com.example.trader.entity.NoteCalendarPerformance;
import com.example.trader.entity.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly=true)
public class NoteCalendarService {
    private final EntityManager em;
    private final NoteBrowseService browse;
    public record Performance(String amount,String rate,String currency,Long version,List<String> tags) {
        public Performance(String amount,String rate,String currency,Long version){this(amount,rate,currency,version,List.of());}
    }
    public record Cell(String key,String start,String end,long count,Performance performance) {}
    public record Window(NoteCalendarPeriod.Unit unit,String anchor,List<Cell> periods,long totalNotes) {}
    public record SaveRequest(NoteCalendarPeriod.Unit unit,String key,String amount,String rate,String currency,Long version) {}
    public record TagSaveRequest(NoteCalendarPeriod.Unit unit,String key,List<String> tags,Long version) {}
    private Performance dto(NoteCalendarPerformance p) {
        return new Performance(p.getAmount()==null?null:p.getAmount().toPlainString(),p.getRate()==null?null:p.getRate().toPlainString(),p.getCurrency(),p.getVersion(),List.copyOf(p.getTags()));
    }
    public Window window(Long owner,NoteCalendarPeriod.Unit unit,String anchor,NoteBrowseFilter filter) {
        var periods=NoteCalendarPeriod.window(unit,anchor);
        var start=periods.get(0).start();var end=periods.get(periods.size()-1).end();
        var days=browse.calendarDays(owner,start,end,filter);
        // Performance is the owner's whole-period manual record, independent of note/ticker filters.
        var values=em.createQuery("select distinct p from NoteCalendarPerformance p left join fetch p.tags where p.user.id=:owner and p.unit=:unit and p.periodStart between :start and :end",NoteCalendarPerformance.class)
                .setParameter("owner",owner).setParameter("unit",unit).setParameter("start",start).setParameter("end",end)
                .setHint("jakarta.persistence.query.timeout",5000).getResultList();
        var cells=periods.stream().map(p->new Cell(p.key(),p.start().toString(),p.end().toString(),
                days.stream().filter(d->!d.date().isBefore(p.start())&&!d.date().isAfter(p.end())).mapToLong(NoteBrowseService.CalendarDay::count).sum(),
                values.stream().filter(v->v.getPeriodStart().equals(p.start())).findFirst().map(this::dto).orElse(null))).toList();
        return new Window(unit,anchor,cells,cells.stream().mapToLong(Cell::count).sum());
    }
    public static BigDecimal decimal(String raw,boolean rate) {
        if(raw==null||raw.isBlank())return null;
        if(!raw.matches(rate?"-?\\d{1,6}(\\.\\d{1,4})?":"-?\\d{1,12}(\\.\\d{1,2})?"))
            throw NoteBrowseFilter.bad(rate?"수익률은 소수 4자리까지 입력해주세요.":"수익금은 정수 12자리·소수 2자리까지 입력해주세요.");
        return new BigDecimal(raw);
    }
    @Transactional
    public Performance save(Long owner,SaveRequest request) {
        if(owner==null)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        var period=NoteCalendarPeriod.parse(request.unit(),request.key());
        if(!List.of("KRW","USD").contains(request.currency()==null?"":request.currency()))throw NoteBrowseFilter.bad("통화는 KRW 또는 USD로 지정해주세요.");
        var amount=decimal(request.amount(),false);var rate=decimal(request.rate(),true);
        var value=findForUpdate(owner,period,request.version());
        if(value.getId()==null){value.change(amount,rate,request.currency());em.persist(value);}
        else value.change(amount,rate,request.currency());
        em.flush();return dto(value);
    }
    @Transactional
    public Performance saveTags(Long owner,TagSaveRequest request) {
        if(owner==null)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        var period=NoteCalendarPeriod.parse(request.unit(),request.key());
        if(request.tags()==null)throw NoteBrowseFilter.bad("태그 목록을 지정해주세요. 모두 지울 때는 빈 목록을 보내주세요.");
        var tags=NoteCalendarTags.normalize(request.tags());
        var value=findForUpdate(owner,period,request.version());
        value.changeTags(tags);
        if(value.getId()==null){value.change(null,null,"KRW");em.persist(value);}
        em.flush();return dto(value);
    }
    private NoteCalendarPerformance findForUpdate(Long owner,NoteCalendarPeriod period,Long version) {
        // Both field-specific writes share the period revision; neither replaces the other fields.
        var user=em.find(User.class,owner,LockModeType.PESSIMISTIC_WRITE);
        if(user==null)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        var rows=em.createQuery("select p from NoteCalendarPerformance p where p.user.id=:owner and p.unit=:unit and p.periodStart=:start",NoteCalendarPerformance.class)
                .setParameter("owner",owner).setParameter("unit",period.unit()).setParameter("start",period.start()).getResultList();
        var value=rows.isEmpty()?null:rows.get(0);
        if(!Objects.equals(value==null?null:value.getVersion(),version))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"다른 화면에서 기간 기록이 변경됐어요. 새로 불러온 뒤 다시 저장해주세요.");
        return value==null?new NoteCalendarPerformance(user,period):value;
    }
}
