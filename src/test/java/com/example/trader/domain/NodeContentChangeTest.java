package com.example.trader.domain;

import com.example.trader.entity.Node;
import com.example.trader.entity.NodeChangeResult;
import com.example.trader.entity.NodeContentChange;
import com.example.trader.entity.NodeContentField;
import com.example.trader.entity.NodeVersionPolicy;
import com.example.trader.entity.Note;
import com.example.trader.exception.InvalidNodeChangeRequestException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NodeContentChangeTest {

    @Test
    @DisplayName("여러 필드 변경은 팀 업무 버전을 정확히 한 번 증가")
    void apply_change_once() {
        Node node = node();
        NodeContentChange change = NodeContentChange.of(Map.of(
                NodeContentField.SUBJECT, "새 제목",
                NodeContentField.CONTENT, "새 내용"
        ));

        NodeChangeResult result = node.applyContentChange(
                change, List.of(), Set.of(), NodeVersionPolicy.ADVANCE_ON_CHANGE
        );

        assertThat(node.getSubject()).isEqualTo("새 제목");
        assertThat(node.getContent()).isEqualTo("새 내용");
        assertThat(node.getVersion()).isEqualTo(4);
        assertThat(result.previousVersion()).isEqualTo(3);
        assertThat(result.newVersion()).isEqualTo(4);
        assertThat(result.appliedFields()).containsExactly("subject", "content");
    }

    @Test
    @DisplayName("빈 변경은 값과 버전을 유지")
    void no_op_keeps_state_and_version() {
        Node node = node();

        NodeChangeResult result = node.applyContentChange(
                NodeContentChange.empty(),
                List.of(),
                Set.of(),
                NodeVersionPolicy.ADVANCE_ON_CHANGE
        );

        assertThat(result.changed()).isFalse();
        assertThat(result.previousVersion()).isEqualTo(3);
        assertThat(result.newVersion()).isEqualTo(3);
        assertThat(node.getVersion()).isEqualTo(3);
    }

    @Test
    @DisplayName("하나의 필드가 유효하지 않으면 앞선 필드도 적용하지 않음")
    void validation_is_atomic() {
        Node node = node();
        EnumMap<NodeContentField, Object> values = new EnumMap<>(NodeContentField.class);
        values.put(NodeContentField.SUBJECT, "변경되면 안 되는 제목");
        values.put(NodeContentField.RECORD_DATE, null);

        assertThatThrownBy(() -> node.applyContentChange(
                NodeContentChange.of(values),
                List.of(),
                Set.of(),
                NodeVersionPolicy.ADVANCE_ON_CHANGE
        )).isInstanceOf(InvalidNodeChangeRequestException.class);

        assertThat(node.getSubject()).isEqualTo("기존 제목");
        assertThat(node.getRecordDate()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(node.getVersion()).isEqualTo(3);
    }

    @Test
    @DisplayName("정규화된 null symbol을 도메인 제거로 적용")
    void clear_symbol_with_normalized_null() {
        Node node = node();
        EnumMap<NodeContentField, Object> values = new EnumMap<>(NodeContentField.class);
        values.put(NodeContentField.SYMBOL, null);

        node.applyContentChange(
                NodeContentChange.of(values),
                List.of(),
                Set.of(),
                NodeVersionPolicy.ADVANCE_ON_CHANGE
        );

        assertThat(node.getSymb()).isNull();
        assertThat(node.getVersion()).isEqualTo(4);
    }

    @Test
    @DisplayName("빈 noteIds 변경은 기존 연결을 실제로 해제")
    void empty_note_ids_clear_links() {
        Note note = Note.builder().id(10L).build();
        Node node = node();
        node.attach(note);
        NodeContentChange change = NodeContentChange.of(Map.of(
                NodeContentField.NOTE_IDS, List.of()
        ));

        NodeChangeResult result = node.applyContentChange(
                change,
                List.of(),
                Set.of(10L),
                NodeVersionPolicy.ADVANCE_ON_CHANGE
        );

        assertThat(node.getNoteLinks()).isEmpty();
        assertThat(result.appliedFields()).containsExactly("noteIds");
    }

    private Node node() {
        return Node.builder()
                .subject("기존 제목")
                .content("기존 내용")
                .symb("AAPL")
                .recordDate(LocalDate.of(2026, 9, 1))
                .version(3)
                .build();
    }
}
