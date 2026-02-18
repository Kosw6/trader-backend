    // NodeRequestDto.java (생성/수정 요청용)
    package com.example.trader.dto.map;

    import com.fasterxml.jackson.annotation.JsonSetter;
    import com.fasterxml.jackson.annotation.Nulls;
    import jakarta.validation.constraints.NotNull;
    import lombok.Getter;
    import lombok.Setter;

    import java.time.LocalDate;
    import java.util.List;
    import java.util.Optional;

    @Getter
    @Setter
    public class RequestNodeDto {
        private Double x;
        private Double y;
        private String subject;
        private String content;
        private String symb;
        private LocalDate recordDate;
        private Long pageId;

        // 1) 아예 누락: 변경 없음
        // 2) 빈 배열: 모두 해제
        // 3) 값 배열: 해당 값으로 동기화
        // noteIds: null 이 오면 "없던 걸로" 취급(= omit과 동일, 변경 없음)
        // 배열 안에 null 원소는 금지하고 싶다면 contentNulls = FAIL 권장
        @JsonSetter(value = "noteIds",nulls = Nulls.SKIP, contentNulls = Nulls.FAIL)
        private List<@NotNull Long> noteIds;
        //빈값이나 null일 경우 -> 변경없음
        public boolean isNoteIdsOmitted() { return noteIds == null; }  // omit/null
        //빈 배열일경우
        public boolean isNoteIdsEmptySet() { return noteIds != null && noteIds.isEmpty(); }
    }