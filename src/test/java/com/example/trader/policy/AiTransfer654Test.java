package com.example.trader.policy;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiTransfer654Test {
    @Test void userResponsesNeverSerializePasswordHashes() throws Exception {
        var value=com.example.trader.dto.ResponseUserDto.builder().id(7L).nickName("test").password("synthetic-hash").build();
        var json=new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(value);
        assertFalse(json.contains("password"));assertFalse(json.contains("synthetic-hash"));
    }
    final String hash="c".repeat(64);
    final PolicyCatalog catalog=new PolicyCatalog(true,"654-test","a".repeat(64),"b".repeat(64));
    @Test void missingStaleFalseAndNullConsentNeverInsert() {
        var jdbc=mock(JdbcTemplate.class);var s=new AiTransferConsent(jdbc,catalog,hash);
        when(jdbc.queryForList(anyString(),eq(7L))).thenReturn(List.of());
        assertEquals(false,s.state(7L).get("accepted"));
        assertThrows(ResponseStatusException.class,()->s.accept(7L,"old",hash,true));
        assertThrows(ResponseStatusException.class,()->s.accept(7L,"654-test","d".repeat(64),true));
        assertThrows(ResponseStatusException.class,()->s.accept(7L,"654-test",hash,false));
        assertThrows(ResponseStatusException.class,()->s.accept(7L,"654-test",hash,null));
        verify(jdbc,never()).update(anyString(),any(Object[].class));
    }
    @Test void withdrawalIsNewestEventAndDoesNotRewriteHistory() {
        var jdbc=mock(JdbcTemplate.class);var s=new AiTransferConsent(jdbc,catalog,hash);
        var yes=Map.<String,Object>of("version","654-test","notice_sha256",hash,"accepted",true);
        var no=Map.<String,Object>of("version","654-test","notice_sha256",hash,"accepted",false);
        when(jdbc.queryForList(anyString(),eq(7L))).thenReturn(List.of(yes),List.of(no));
        assertEquals(true,s.state(7L).get("accepted"));
        assertEquals(false,s.withdraw(7L).get("accepted"));
        verify(jdbc).update(startsWith("INSERT INTO"),eq(7L),eq("654-test"),eq(hash),eq(false));
    }
    @Test void previousNoticeDoesNotAuthorizeNewNotice() {
        var jdbc=mock(JdbcTemplate.class);var s=new AiTransferConsent(jdbc,catalog,hash);
        when(jdbc.queryForList(anyString(),eq(7L))).thenReturn(List.of(Map.of("version","old","notice_sha256",hash,"accepted",true)));
        assertEquals(false,s.state(7L).get("accepted"));
    }
    @Test void under14DeclarationRejectedAndLegacyProfileInputsDiscarded() {
        assertThrows(ResponseStatusException.class,()->catalog.validate(new PolicyAcceptance("654-test","a".repeat(64),"b".repeat(64),true,true,false)));
        assertThrows(ResponseStatusException.class,()->catalog.validate(new PolicyAcceptance("654-test","a".repeat(64),"b".repeat(64),true,true,null)));
    }
}
