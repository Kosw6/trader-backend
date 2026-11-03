package com.example.trader.security.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.assertj.core.api.Assertions.*;

class NoteUploadBodyFilterTest {
    final NoteUploadBodyFilter filter=new NoteUploadBodyFilter(new ObjectMapper());
    @BeforeEach void auth() {SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated("synthetic","",List.of()));}
    @AfterEach void clear() {SecurityContextHolder.clearContext();}
    MockHttpServletResponse send(byte[] bytes,String type,String encoding) throws Exception {
        var request=new MockHttpServletRequest("PUT","/api/note/uploads/00000000-0000-0000-0000-000000000000/parts/0") {
            @Override public int getContentLength(){return -1;}
            @Override public long getContentLengthLong(){return -1;}
        };
        request.setContent(bytes);if(type!=null)request.setContentType(type);if(encoding!=null)request.addHeader("Content-Encoding",encoding);
        var response=new MockHttpServletResponse();filter.doFilter(request,response,(req,res)->{
            assertThat(req.getInputStream().readAllBytes()).isEqualTo(bytes);res.getWriter().write("OK");
        });return response;
    }
    @Test void exactWireLimitAndUnknownLengthAreBounded() throws Exception {
        String body="{\"content\":\"x\"}";body+=" ".repeat(7168-body.length());
        assertThat(send(body.getBytes(StandardCharsets.UTF_8),"application/json",null).getContentAsString()).isEqualTo("OK");
        assertThat(send((body+" ").getBytes(StandardCharsets.UTF_8),"application/json",null).getStatus()).isEqualTo(413);
    }
    @Test void rawPartLimitIsIndependentOfWireLimit() throws Exception {
        var mapper=new ObjectMapper();
        assertThat(send(mapper.writeValueAsBytes(java.util.Map.of("content","가".repeat(1366))),"application/json",null).getStatus()).isEqualTo(413);
    }
    @Test void rejectsMalformedDuplicateTrailingAndCompressedJson() throws Exception {
        for(String json:List.of("{}{}","{\"content\":\"a\",\"content\":\"b\"}","[]","{"))
            assertThat(send(json.getBytes(StandardCharsets.UTF_8),"application/json",null).getStatus()).isEqualTo(400);
        assertThat(send(new byte[]{'{','"','x','"',':','"',(byte)0xc0,(byte)0xaf,'"','}'},"application/json",null).getStatus()).isEqualTo(400);
        assertThat(send("{}".getBytes(),"application/json","gzip").getStatus()).isEqualTo(415);
        assertThat(send("{}".getBytes(),"application/json;charset=UTF-16",null).getStatus()).isEqualTo(415);
    }
    @Test void emptyCompletionHasNoContentTypeRequirement() throws Exception {
        assertThat(send(new byte[0],null,null).getContentAsString()).isEqualTo("OK");
    }
}
