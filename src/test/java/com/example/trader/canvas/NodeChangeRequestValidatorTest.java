package com.example.trader.canvas;

import com.example.trader.dto.map.UpdatePersonalNodeContentRequest;
import com.example.trader.dto.map.UpdateTeamNodeContentRequest;
import com.example.trader.entity.Node;
import com.example.trader.entity.NodeChangePlan;
import com.example.trader.exception.InvalidNodeChangeRequestException;
import com.example.trader.exception.NodeChangeErrorCode;
import com.example.trader.service.NodeChangeRequestValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NodeChangeRequestValidatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final NodeChangeRequestValidator validator = new NodeChangeRequestValidator();
    private Node node;

    @BeforeEach
    void setUp() {
        node = Node.builder()
                .subject("기존 제목")
                .content("기존 내용")
                .symb("AAPL")
                .recordDate(LocalDate.of(2026, 9, 1))
                .build();
    }

    @Test
    @DisplayName("dirtyFields에 선언하지 않은 본문 값은 거절")
    void reject_undeclared_body_value() throws Exception {
        UpdateTeamNodeContentRequest request = teamRequest("""
                {"subject":"새 제목","content":"새 내용","dirtyFields":["subject"],"baseVersion":0}
                """);

        assertError(
                () -> validator.validateTeam(request, node, Set.of()),
                NodeChangeErrorCode.UNDECLARED_FIELD_VALUE
        );
    }

    @Test
    @DisplayName("dirtyFields에 선언했지만 본문 값이 누락되면 거절")
    void reject_missing_declared_value() throws Exception {
        UpdateTeamNodeContentRequest request = teamRequest("""
                {"dirtyFields":["content"],"baseVersion":0}
                """);

        assertError(
                () -> validator.validateTeam(request, node, Set.of()),
                NodeChangeErrorCode.DIRTY_FIELD_VALUE_MISSING
        );
    }

    @Test
    @DisplayName("dirtyFields 누락과 빈 배열을 서로 다른 오류로 보존")
    void distinguish_missing_and_empty_dirty_fields() throws Exception {
        UpdateTeamNodeContentRequest missing = teamRequest("""
                {"subject":"새 제목","baseVersion":0}
                """);
        UpdateTeamNodeContentRequest empty = teamRequest("""
                {"dirtyFields":[],"baseVersion":0}
                """);

        assertError(
                () -> validator.validateTeam(missing, node, Set.of()),
                NodeChangeErrorCode.DIRTY_FIELDS_REQUIRED
        );
        assertError(
                () -> validator.validateTeam(empty, node, Set.of()),
                NodeChangeErrorCode.DIRTY_FIELDS_EMPTY
        );
    }

    @Test
    @DisplayName("팀 수정은 0 이상의 baseVersion을 필수로 요구")
    void require_valid_team_base_version() throws Exception {
        UpdateTeamNodeContentRequest missing = teamRequest("""
                {"subject":"새 제목","dirtyFields":["subject"]}
                """);
        UpdateTeamNodeContentRequest negative = teamRequest("""
                {"subject":"새 제목","dirtyFields":["subject"],"baseVersion":-1}
                """);

        assertError(
                () -> validator.validateTeam(missing, node, Set.of()),
                NodeChangeErrorCode.BASE_VERSION_REQUIRED
        );
        assertError(
                () -> validator.validateTeam(negative, node, Set.of()),
                NodeChangeErrorCode.INVALID_BASE_VERSION
        );
    }

    @Test
    @DisplayName("내용 수정 API의 x,y 입력은 위치 계약 위반")
    void reject_position_fields() throws Exception {
        UpdateTeamNodeContentRequest request = teamRequest("""
                {"x":10,"subject":"새 제목","dirtyFields":["subject"],"baseVersion":0}
                """);

        assertError(
                () -> validator.validateTeam(request, node, Set.of()),
                NodeChangeErrorCode.POSITION_FIELD_NOT_ALLOWED
        );
    }

    @Test
    @DisplayName("subject 빈 문자열과 null을 구분해 거절")
    void reject_blank_and_null_subject() throws Exception {
        UpdateTeamNodeContentRequest blank = teamRequest("""
                {"subject":"","dirtyFields":["subject"],"baseVersion":0}
                """);
        UpdateTeamNodeContentRequest explicitNull = teamRequest("""
                {"subject":null,"dirtyFields":["subject"],"baseVersion":0}
                """);

        assertError(
                () -> validator.validateTeam(blank, node, Set.of()),
                NodeChangeErrorCode.EMPTY_SUBJECT_NOT_ALLOWED
        );
        assertError(
                () -> validator.validateTeam(explicitNull, node, Set.of()),
                NodeChangeErrorCode.NULL_VALUE_NOT_ALLOWED
        );
    }

    @Test
    @DisplayName("content 빈 문자열은 비우기로 허용하고 null은 거절")
    void content_empty_is_valid_but_null_is_not() throws Exception {
        UpdateTeamNodeContentRequest empty = teamRequest("""
                {"content":"","dirtyFields":["content"],"baseVersion":0}
                """);
        UpdateTeamNodeContentRequest explicitNull = teamRequest("""
                {"content":null,"dirtyFields":["content"],"baseVersion":0}
                """);

        NodeChangePlan plan = validator.validateTeam(empty, node, Set.of());

        assertThat(plan.effectiveChange().externalValues()).containsEntry("content", "");
        assertError(
                () -> validator.validateTeam(explicitNull, node, Set.of()),
                NodeChangeErrorCode.NULL_VALUE_NOT_ALLOWED
        );
    }

    @Test
    @DisplayName("symb 빈 문자열은 null 제거 값으로 정규화")
    void normalize_empty_symbol_to_null() throws Exception {
        UpdateTeamNodeContentRequest request = teamRequest("""
                {"symb":"","dirtyFields":["symb"],"baseVersion":0}
                """);

        NodeChangePlan plan = validator.validateTeam(request, node, Set.of());

        assertThat(plan.effectiveChange().externalValues()).containsEntry("symb", null);
    }

    @Test
    @DisplayName("recordDate 명시적 null은 누락이 아니라 잘못된 변경")
    void reject_explicit_null_record_date() throws Exception {
        UpdateTeamNodeContentRequest request = teamRequest("""
                {"recordDate":null,"dirtyFields":["recordDate"],"baseVersion":0}
                """);

        assertError(
                () -> validator.validateTeam(request, node, Set.of()),
                NodeChangeErrorCode.NULL_VALUE_NOT_ALLOWED
        );
    }

    @Test
    @DisplayName("noteIds 누락은 보존하고 빈 배열은 연결 해제 의도로 구분")
    void distinguish_omitted_and_empty_note_ids() throws Exception {
        UpdatePersonalNodeContentRequest omitted = personalRequest("""
                {"subject":"새 제목"}
                """);
        UpdatePersonalNodeContentRequest empty = personalRequest("""
                {"noteIds":[]}
                """);

        NodeChangePlan omittedPlan = validator.validatePersonal(omitted, node, Set.of(10L));
        NodeChangePlan emptyPlan = validator.validatePersonal(empty, node, Set.of(10L));

        assertThat(omittedPlan.effectiveChange().fieldNames()).containsExactly("subject");
        assertThat(emptyPlan.effectiveChange().fieldNames()).containsExactly("noteIds");
        assertThat(emptyPlan.effectiveChange().noteIds()).isEmpty();
    }

    @Test
    @DisplayName("noteIds 명시적 null은 누락과 같이 기존 연결을 보존")
    void explicit_null_note_ids_are_treated_as_omitted() throws Exception {
        UpdatePersonalNodeContentRequest request = personalRequest("""
                {"subject":"새 제목","noteIds":null}
                """);

        NodeChangePlan plan = validator.validatePersonal(request, node, Set.of(10L));

        assertThat(request.isNoteIdsSupplied()).isFalse();
        assertThat(plan.effectiveChange().fieldNames()).containsExactly("subject");
    }

    @Test
    @DisplayName("개인 수정은 본문에 포함된 필드 자체를 변경 의도로 사용")
    void personal_request_uses_supplied_fields_as_change_intent() throws Exception {
        UpdatePersonalNodeContentRequest request = personalRequest("""
                {"subject":"새 제목","content":"기존 내용"}
                """);

        NodeChangePlan plan = validator.validatePersonal(request, node, Set.of());

        assertThat(plan.requestedFields()).containsExactly("subject", "content");
        assertThat(plan.effectiveChange().fieldNames()).containsExactly("subject");
    }

    @Test
    @DisplayName("개인 수정의 빈 본문은 변경 의도가 없어 거절")
    void reject_empty_personal_request() throws Exception {
        UpdatePersonalNodeContentRequest request = personalRequest("{}");

        assertError(
                () -> validator.validatePersonal(request, node, Set.of()),
                NodeChangeErrorCode.CHANGE_FIELDS_REQUIRED
        );
    }

    @Test
    @DisplayName("개인 수정에 dirtyFields를 보내면 지원하지 않는 중복 계약으로 거절")
    void reject_dirty_fields_in_personal_request() throws Exception {
        UpdatePersonalNodeContentRequest request = personalRequest("""
                {"subject":"새 제목","dirtyFields":["subject"]}
                """);

        assertError(
                () -> validator.validatePersonal(request, node, Set.of()),
                NodeChangeErrorCode.DIRTY_FIELDS_NOT_ALLOWED
        );
    }

    @Test
    @DisplayName("같은 값은 요청 필드에는 남지만 실제 적용 필드에서는 제거")
    void same_value_becomes_no_op() throws Exception {
        UpdateTeamNodeContentRequest request = teamRequest("""
                {"subject":"기존 제목","dirtyFields":["subject"],"baseVersion":0}
                """);

        NodeChangePlan plan = validator.validateTeam(request, node, Set.of());

        assertThat(plan.requestedFields()).containsExactly("subject");
        assertThat(plan.isNoOp()).isTrue();
    }

    @Test
    @DisplayName("일부만 같은 값이면 실제로 달라지는 필드만 적용 대상으로 계산")
    void partially_same_request_keeps_only_effective_fields() throws Exception {
        UpdateTeamNodeContentRequest request = teamRequest("""
                {"subject":"기존 제목","content":"새 내용","dirtyFields":["subject","content"],"baseVersion":0}
                """);

        NodeChangePlan plan = validator.validateTeam(request, node, Set.of());

        assertThat(plan.requestedFields()).containsExactly("subject", "content");
        assertThat(plan.effectiveChange().fieldNames()).containsExactly("content");
        assertThat(plan.effectiveChange().externalValues()).containsOnly(Map.entry("content", "새 내용"));
    }

    @Test
    @DisplayName("중복 또는 지원하지 않는 dirty field는 조용히 제거하지 않음")
    void reject_duplicate_and_unsupported_dirty_fields() throws Exception {
        UpdateTeamNodeContentRequest duplicate = teamRequest("""
                {"subject":"새 제목","dirtyFields":["subject","subject"],"baseVersion":0}
                """);
        UpdateTeamNodeContentRequest unsupported = teamRequest("""
                {"subject":"새 제목","dirtyFields":["unknown"],"baseVersion":0}
                """);

        assertError(
                () -> validator.validateTeam(duplicate, node, Set.of()),
                NodeChangeErrorCode.DUPLICATE_DIRTY_FIELD
        );
        assertError(
                () -> validator.validateTeam(unsupported, node, Set.of()),
                NodeChangeErrorCode.UNSUPPORTED_DIRTY_FIELD
        );
    }

    private UpdateTeamNodeContentRequest teamRequest(String json) throws Exception {
        return objectMapper.readValue(json, UpdateTeamNodeContentRequest.class);
    }

    private UpdatePersonalNodeContentRequest personalRequest(String json) throws Exception {
        return objectMapper.readValue(json, UpdatePersonalNodeContentRequest.class);
    }

    private void assertError(Runnable action, NodeChangeErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(
                        InvalidNodeChangeRequestException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(errorCode)
                );
    }
}
