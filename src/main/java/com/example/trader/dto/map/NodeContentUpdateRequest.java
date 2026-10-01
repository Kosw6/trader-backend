package com.example.trader.dto.map;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 내용 변경 API가 값의 누락과 명시적 null을 구분할 수 있게 하는 요청 계약.
 */
public abstract class NodeContentUpdateRequest {
    private String subject;
    private String content;
    private String symb;
    private LocalDate recordDate;
    private List<@NotNull Long> noteIds;

    private boolean subjectSupplied;
    private boolean contentSupplied;
    private boolean symbSupplied;
    private boolean recordDateSupplied;
    private boolean noteIdsSupplied;
    private final Map<String, Object> unsupportedValues = new LinkedHashMap<>();

    public String getSubject() { return subject; }
    public String getContent() { return content; }
    public String getSymb() { return symb; }
    public LocalDate getRecordDate() { return recordDate; }
    public List<Long> getNoteIds() { return noteIds; }

    @JsonSetter("subject")
    public void setSubject(String subject) {
        this.subjectSupplied = true;
        this.subject = subject;
    }

    @JsonSetter("content")
    public void setContent(String content) {
        this.contentSupplied = true;
        this.content = content;
    }

    @JsonSetter("symb")
    public void setSymb(String symb) {
        this.symbSupplied = true;
        this.symb = symb;
    }

    @JsonSetter("recordDate")
    public void setRecordDate(LocalDate recordDate) {
        this.recordDateSupplied = true;
        this.recordDate = recordDate;
    }

    // 명시적 null은 누락과 같은 의미다. 배열 내부 null은 잘못된 요청이다.
    @JsonSetter(value = "noteIds", nulls = Nulls.SKIP, contentNulls = Nulls.FAIL)
    public void setNoteIds(List<Long> noteIds) {
        this.noteIdsSupplied = true;
        this.noteIds = noteIds;
    }

    @JsonAnySetter
    public void captureUnsupportedValue(String name, Object value) {
        unsupportedValues.put(name, value);
    }

    @JsonIgnore public boolean isSubjectSupplied() { return subjectSupplied; }
    @JsonIgnore public boolean isContentSupplied() { return contentSupplied; }
    @JsonIgnore public boolean isSymbSupplied() { return symbSupplied; }
    @JsonIgnore public boolean isRecordDateSupplied() { return recordDateSupplied; }
    @JsonIgnore public boolean isNoteIdsSupplied() { return noteIdsSupplied; }
    @JsonIgnore
    public Map<String, Object> getUnsupportedValues() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(unsupportedValues));
    }

    @JsonIgnore
    public boolean isFieldSupplied(String field) {
        return switch (field) {
            case "subject" -> subjectSupplied;
            case "content" -> contentSupplied;
            case "symb" -> symbSupplied;
            case "recordDate" -> recordDateSupplied;
            case "noteIds" -> noteIdsSupplied;
            default -> false;
        };
    }

    @JsonIgnore
    public Object valueOf(String field) {
        return switch (field) {
            case "subject" -> subject;
            case "content" -> content;
            case "symb" -> symb;
            case "recordDate" -> recordDate;
            case "noteIds" -> noteIds;
            default -> null;
        };
    }
}
