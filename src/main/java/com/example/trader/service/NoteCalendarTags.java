package com.example.trader.service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

/** User-authored period labels, separate from security/stock ticker identities. */
public final class NoteCalendarTags {
    private NoteCalendarTags() {}
    public static final int MAX_TAGS=12;
    public static List<String> normalize(List<String> raw) {
        if(raw==null)return List.of();
        if(raw.size()>48)throw NoteBrowseFilter.bad("기간 태그는 최대 12개까지 입력해주세요.");
        var result=new ArrayList<String>();var seen=new HashSet<String>();
        for(String value:raw) {
            if(value==null||value.length()>200)throw NoteBrowseFilter.bad("태그는 공백 없이 20자 이내로 입력해주세요.");
            var tag=Normalizer.normalize(value,Normalizer.Form.NFKC).strip().replaceFirst("^#+","").strip();
            if(tag.codePointCount(0,tag.length())>20||!tag.matches("[\\p{L}\\p{N}][\\p{L}\\p{M}\\p{N}_·.\\-]*"))
                throw NoteBrowseFilter.bad("태그는 글자·숫자로 시작하고, 공백 없이 20자 이내로 입력해주세요.");
            if(seen.add(tag.toLowerCase(Locale.ROOT)))result.add(tag);
        }
        if(result.size()>MAX_TAGS)throw NoteBrowseFilter.bad("기간 태그는 최대 12개까지 입력해주세요.");
        return List.copyOf(result);
    }
}
