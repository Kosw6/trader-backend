package com.example.trader.ai;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class AiAuthContractTest {
    private final Instant now = Instant.parse("2026-10-02T00:00:00Z");
    private final String retrieval = "r".repeat(43), indexing = "i".repeat(43);
    private final AiServiceAuthFilter filter = new AiServiceAuthFilter(true, retrieval, indexing,120,5,Clock.fixed(now,ZoneOffset.UTC));
    private MockHttpServletRequest request(String path, String key) {
        var request = new MockHttpServletRequest("GET",path);
        request.addHeader("Authorization","Bearer " + key);
        return request;
    }
    private void delegate(MockHttpServletRequest request) {
        request.addHeader("X-AI-User-Id","1");
        request.addHeader("X-AI-Accepted-At",now.toString());
        request.addHeader("X-AI-Deadline-At",now.plusSeconds(120).toString());
    }
    @Test void exact_title_requires_retrieval_identity_and_delegation() throws Exception {
        for (String key : List.of(retrieval,indexing,"invalid")) {
            var req=request("/internal/ai/retrieval/exact-title",key); delegate(req);
            var res=new MockHttpServletResponse(); var chain=new MockFilterChain();
            filter.doFilter(req,res,chain);
            assertThat(res.getStatus()).isEqualTo(key.equals(retrieval)?200:key.equals(indexing)?403:401);
        }
        var req=request("/internal/ai/retrieval/exact-title",retrieval);
        var res=new MockHttpServletResponse(); filter.doFilter(req,res,new MockFilterChain());
        assertThat(res.getStatus()).isEqualTo(400);
    }
    @Test void consent_read_requires_retrieval_key_user_and_read_only_method() throws Exception {
        for (String key : List.of(retrieval,indexing,"invalid")) {
            var req=request("/internal/ai/retrieval/transfer-consent",key);delegate(req);
            var res=new MockHttpServletResponse();var chain=new MockFilterChain();filter.doFilter(req,res,chain);
            assertThat(res.getStatus()).isEqualTo(key.equals(retrieval)?200:key.equals(indexing)?403:401);
            assertThat(chain.getRequest()!=null).isEqualTo(key.equals(retrieval));
        }
        var missing=request("/internal/ai/retrieval/transfer-consent",retrieval);
        var res=new MockHttpServletResponse();filter.doFilter(missing,res,new MockFilterChain());assertThat(res.getStatus()).isEqualTo(400);
        var write=request("/internal/ai/retrieval/transfer-consent",retrieval);delegate(write);write.setMethod("POST");
        res=new MockHttpServletResponse();filter.doFilter(write,res,new MockFilterChain());assertThat(res.getStatus()).isEqualTo(403);
    }
    @Test void separates_service_roles_and_requires_delegation() throws Exception {
        for (String key : List.of(retrieval,indexing,"invalid")) {
            var req=request("/internal/ai/retrieval/sources/NOTE/1",key); delegate(req);
            var res=new MockHttpServletResponse(); var chain=new MockFilterChain(); filter.doFilter(req,res,chain);
            assertThat(res.getStatus()).isEqualTo(key.equals(retrieval)?200:key.equals(indexing)?403:401);
            assertThat(chain.getRequest()!=null).isEqualTo(key.equals(retrieval));
        }
        var req=request("/internal/ai/retrieval/sources/NOTE/1",retrieval);
        var res=new MockHttpServletResponse(); filter.doFilter(req,res,new MockFilterChain());
        assertThat(res.getStatus()).isEqualTo(400);
    }
    @Test void indexing_cannot_accept_user_context() throws Exception {
        var req=request("/internal/ai/indexing/sources/NOTE/1",indexing); delegate(req);
        var res=new MockHttpServletResponse(); filter.doFilter(req,res,new MockFilterChain());
        assertThat(res.getStatus()).isEqualTo(400);
    }
    @Test void path_method_duplicate_header_and_deadline_are_enforced() throws Exception {
        for (int scenario=0;scenario<5;scenario++) {
            var req=request("/internal/ai/retrieval/sources/NOTE/1",retrieval); delegate(req);
            switch(scenario) {
                case 0 -> req.setMethod("POST");
                case 1 -> req.setRequestURI("/internal/ai/admin");
                case 2 -> req.addHeader("X-AI-User-Id","2");
                case 3 -> { req.removeHeader("X-AI-Deadline-At");req.addHeader("X-AI-Deadline-At",now.toString()); }
                case 4 -> req.addParameter("scopeType","PERSONAL","TEAM");
            }
            var res=new MockHttpServletResponse();var chain=new MockFilterChain();filter.doFilter(req,res,chain);
            assertThat(res.getStatus()).isGreaterThanOrEqualTo(400); assertThat(chain.getRequest()).isNull();
            assertThat(res.getContentAsString()).doesNotContain(retrieval,indexing);
        }
    }
    @Test void disabled_is_fail_closed_and_invalid_config_fails_startup() throws Exception {
        var res=new MockHttpServletResponse();
        new AiServiceAuthFilter(false,"","",120,5,Clock.systemUTC()).doFilter(request("/internal/ai/admin",retrieval),res,new MockFilterChain());
        assertThat(res.getStatus()).isEqualTo(503);
        assertThatThrownBy(()->new AiServiceAuthFilter(true,retrieval,retrieval,120,5,Clock.systemUTC())).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void cursor_binds_scope_revision_and_expiry_and_supports_rotation() {
        byte[] old = new byte[32]; Arrays.fill(old,(byte)1);
        byte[] next = new byte[32]; Arrays.fill(next,(byte)2);
        var codec=new AiCursorCodec("old",Map.of("old",old),300,Clock.fixed(now,ZoneOffset.UTC));
        var binding=Map.<String,Object>of("revision","8","scope",new AiScope(false,1L,"TEAM",7L).canonical());
        String token=codec.encode(binding,10);
        assertThat(codec.decode(token,binding)).isEqualTo(10);
        assertThat(new AiCursorCodec("new",Map.of("old",old,"new",next),300,Clock.fixed(now,ZoneOffset.UTC)).decode(token,binding)).isEqualTo(10);
        assertThatThrownBy(()->codec.decode(token,Map.of("revision","9"))).isInstanceOf(AiApiException.class);
        assertThatThrownBy(()->codec.decode(token.substring(0,token.length()-3)+"aaa",binding)).isInstanceOf(AiApiException.class);
        assertThatThrownBy(()->new AiCursorCodec("old",Map.of("old",old),300,Clock.fixed(now.plusSeconds(301),ZoneOffset.UTC)).decode(token,binding)).isInstanceOf(AiApiException.class);
    }
    @Test void canonical_json_preserves_nullable_and_string_bytes() {
        var document=new LinkedHashMap<String,Object>();
        document.put("title","제목"); document.put("content","a\nb"); document.put("stockSymbol",null); document.put("noteDate","2026-10-02");
        assertThat(AiCanonicalJson.documentHash("NOTE",document)).isEqualTo("a667804348166e4182c733ca70c033049d4f35e289b3a4690e7a56da6355c124");
        String original=AiCanonicalJson.documentHash("NOTE",document);
        document.put("canvasId","3"); assertThat(AiCanonicalJson.documentHash("NOTE",document)).isEqualTo(original);
        document.put("content","a\r\nb"); assertThat(AiCanonicalJson.documentHash("NOTE",document)).isNotEqualTo(original);
        assertThat(AiCanonicalJson.bytes(Map.of("text","한글\n\""))).isEqualTo("{\"text\":\"한글\\n\\\"\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
