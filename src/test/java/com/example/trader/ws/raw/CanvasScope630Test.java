package com.example.trader.ws.raw;

import com.example.trader.repository.UserTeamRepository;
import com.example.trader.repository.PageRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.CloseStatus;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CanvasScope630Test {
    UserTeamRepository members = mock(UserTeamRepository.class);
    PageRepository pages = mock(PageRepository.class);
    CanvasSessionRegistry registry = new CanvasSessionRegistry(members, pages);
    WebSocketSession session(long user) {
        var s = mock(WebSocketSession.class);
        when(s.getAttributes()).thenReturn(Map.of(WsAttrs.USER_ID,user));
        return s;
    }
    @Test void connectionRequiresMembershipAndGraphInSameTeam() {
        when(members.existsByTeamIdAndUserId(1L,3L)).thenReturn(true);
        when(pages.existsByIdAndDirectoryTeamId(2L,1L)).thenReturn(true);
        assertTrue(registry.authorized(1L,2L,3L));
        assertFalse(registry.authorized(1L,2L,4L));
        assertFalse(registry.authorized(1L,5L,3L));
    }
    @Test void missingOrInvalidIdentityIsRejected() {
        assertFalse(registry.authorized(1L,2L,null));
        assertFalse(registry.authorized(1L,2L,"3"));
        assertFalse(registry.authorized(-1L,2L,3L));
        verifyNoInteractions(members,pages);
    }
    @Test void authorizationFailureIsClosed() {
        when(members.existsByTeamIdAndUserId(1L,3L)).thenThrow(new IllegalStateException());
        assertFalse(registry.authorized(1L,2L,3L));
    }
    @Test void revokedRecipientCannotReceiveNextBroadcast() throws Exception {
        var allowed=session(3);var revoked=session(4);
        registry.join("team:1:graph:2",allowed);registry.join("team:1:graph:2",revoked);
        when(pages.existsByIdAndDirectoryTeamId(2L,1L)).thenReturn(true);
        when(members.findAuthorizedUserIds(1L)).thenReturn(List.of(3L));
        assertEquals(List.of(allowed),registry.snapshot("team:1:graph:2"));
        verify(revoked).close(CloseStatus.POLICY_VIOLATION);
        verify(allowed,never()).close(any());
        verify(members,times(1)).findAuthorizedUserIds(1L);
    }
    @Test void removedCanvasCannotBroadcast() throws Exception {
        var s=session(3);registry.join("team:1:graph:2",s);
        assertTrue(registry.snapshot("team:1:graph:2").isEmpty());
        verify(s).close(CloseStatus.POLICY_VIOLATION);
    }
    @Test void databaseFailureCannotBroadcast() throws Exception {
        var s=session(3);registry.join("team:1:graph:2",s);
        when(pages.existsByIdAndDirectoryTeamId(2L,1L)).thenReturn(true);
        when(members.findAuthorizedUserIds(1L)).thenThrow(new IllegalStateException());
        assertTrue(registry.snapshot("team:1:graph:2").isEmpty());
        verify(s).close(CloseStatus.POLICY_VIOLATION);
    }
}
