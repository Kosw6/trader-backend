package com.example.trader.dto.map;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
public class CreateNodeRequest {
    private Double x;
    private Double y;
    private String subject;
    private String content;
    private String symb;
    private LocalDate recordDate;

    @JsonSetter(value = "noteIds", nulls = Nulls.SKIP, contentNulls = Nulls.FAIL)
    private List<@NotNull Long> noteIds;

    public boolean isNoteIdsOmitted() {
        return noteIds == null;
    }
}
