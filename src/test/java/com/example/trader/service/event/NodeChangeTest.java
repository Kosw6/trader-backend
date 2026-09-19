package com.example.trader.service.event;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class NodeChangeTest {
    @Test
    void applied_fields_are_an_immutable_snapshot() {
        var fields = new ArrayList<>(List.of("subject"));
        var change = NodeChange.updated(1L, 2L, 3L, 4L, fields, 5);
        fields.clear();
        assertThat(change.fields()).containsExactly("subject");
        assertThatThrownBy(() -> change.fields().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejects_missing_identity_actor_version_and_invalid_fields() {
        assertThatThrownBy(() -> NodeChange.created(null, 2L, 3L, 4L, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> NodeChange.created(1L, 2L, 3L, null, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> NodeChange.created(1L, 2L, 3L, 4L, -1))
                .isInstanceOf(IllegalArgumentException.class);
        for (var fields : List.of(List.<String>of(), List.of("x"), List.of("subject", "subject"))) {
            assertThatThrownBy(() -> NodeChange.updated(1L, 2L, 3L, 4L, fields, 5))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> NodeChange.positionUpdated(1L, 2L, 3L, 4L, 5, Double.NaN, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void canonical_constructor_cannot_bypass_event_specific_contracts() {
        assertThatThrownBy(() -> new NodeChange(NodeChange.Kind.POSITION_UPDATED,
                1L, 2L, 3L, 4L, List.of("subject"), 5, 0.0, 0.0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NodeChange(NodeChange.Kind.DELETED,
                1L, 2L, 3L, null, List.of("node"), null, 1.0, 2.0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(NodeChange.deleted(1L, 2L, 3L).version()).isNull();
    }
}
