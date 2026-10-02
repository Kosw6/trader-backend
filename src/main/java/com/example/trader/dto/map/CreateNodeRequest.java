package com.example.trader.dto.map;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
public class CreateNodeRequest {
    private Double x;
    private Double y;
    @NotBlank(message = "제목을 입력해주세요")
    private String subject;
    @NotNull(message = "내용은 null일 수 없습니다")
    private String content;
    private String symb;
    @NotNull(message = "기록 날짜를 입력해주세요")
    private LocalDate recordDate;

    @JsonSetter(value = "noteIds", nulls = Nulls.SKIP, contentNulls = Nulls.FAIL)
    private List<@NotNull Long> noteIds;

    public boolean isNoteIdsOmitted() {
        return noteIds == null;
    }
}
