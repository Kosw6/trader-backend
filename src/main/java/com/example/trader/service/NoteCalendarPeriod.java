package com.example.trader.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

public record NoteCalendarPeriod(Unit unit, String key, LocalDate start, LocalDate end) {
    public enum Unit { YEAR, MONTH, DAY }
    public static NoteCalendarPeriod parse(Unit unit, String key) {
        try {
            if (unit == null || key == null) throw new IllegalArgumentException();
            LocalDate start, end;
            switch (unit) {
                case YEAR -> {
                    if (!key.matches("\\d{4}")) throw new IllegalArgumentException();
                    int year=Integer.parseInt(key);start=LocalDate.of(year,1,1);end=LocalDate.of(year,12,31);
                }
                case MONTH -> {
                    if (!key.matches("\\d{4}-\\d{2}")) throw new IllegalArgumentException();
                    var month=YearMonth.parse(key);start=month.atDay(1);end=month.atEndOfMonth();
                }
                default -> {
                    if (!key.matches("\\d{4}-\\d{2}-\\d{2}")) throw new IllegalArgumentException();
                    start=LocalDate.parse(key);end=start;
                }
            }
            if (start.getYear()<1 || end.getYear()>9999) throw new IllegalArgumentException();
            return new NoteCalendarPeriod(unit,key,start,end);
        } catch (RuntimeException ex) { throw NoteBrowseFilter.bad("기간 형식을 확인해주세요. 연도 YYYY · 월 YYYY-MM · 날짜 YYYY-MM-DD"); }
    }
    public static List<NoteCalendarPeriod> window(Unit unit,String anchor) {
        if(unit==null)throw NoteBrowseFilter.bad("캘린더 단위를 지정해주세요.");
        var result=new ArrayList<NoteCalendarPeriod>();
        if(unit==Unit.DAY) {
            var month=parse(Unit.MONTH,anchor);
            for(var d=month.start();!d.isAfter(month.end());d=d.plusDays(1))result.add(parse(Unit.DAY,d.toString()));
        } else {
            var year=parse(Unit.YEAR,anchor);int first=year.start().getYear();
            if(unit==Unit.MONTH)for(int m=1;m<=12;m++)result.add(parse(unit,String.format("%04d-%02d",first,m)));
            else for(int y=first;y<=Math.min(9999,first+9);y++)result.add(parse(Unit.YEAR,String.format("%04d",y)));
        }
        return List.copyOf(result);
    }
}
