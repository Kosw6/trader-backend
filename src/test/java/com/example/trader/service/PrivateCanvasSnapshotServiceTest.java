package com.example.trader.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class PrivateCanvasSnapshotServiceTest {
    @Test void quotaIsFiveSlotsAndReusesOnlyEmptySlot() {
        assertThat(PrivateCanvasSnapshotService.freeSlot(List.of())).isEqualTo(1);
        assertThat(PrivateCanvasSnapshotService.freeSlot(List.of(1,2,4,5))).isEqualTo(3);
        assertThatThrownBy(()->PrivateCanvasSnapshotService.freeSlot(List.of(1,2,3,4,5)))
            .isInstanceOf(ResponseStatusException.class);
    }
    @Test void titleContract() {
        assertThat(PrivateCanvasSnapshotService.title("  근거 보관  ")).isEqualTo("근거 보관");
        for(String bad:List.of("","  ","a".repeat(121)))
            assertThatThrownBy(()->PrivateCanvasSnapshotService.title(bad)).isInstanceOf(ResponseStatusException.class);
    }
    @Test void payloadNeverJoinsPrivateNoteTablesAndCapturesOneStatement() {
        assertThat(PrivateCanvasSnapshotService.CAPTURE).doesNotContain("node_note_link","FROM note","SELECT *");
        assertThat(PrivateCanvasSnapshotService.CAPTURE).contains("ut.user_id=?","n.page_id=p.id","e.page_id=p.id");
    }
}
