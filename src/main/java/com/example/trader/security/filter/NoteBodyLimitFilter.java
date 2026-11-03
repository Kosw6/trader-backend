package com.example.trader.security.filter;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.http.MediaType;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.Map;

/** Runs after Spring Security. Bound authenticated JSON before MVC or database work. */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class NoteBodyLimitFilter extends OncePerRequestFilter {
    public static final int REQUEST_BYTES = 64 * 1024;
    public static final int CONTENT_BYTES = 48 * 1024;
    private final ObjectMapper mapper;

    public NoteBodyLimitFilter(ObjectMapper mapper) { this.mapper = mapper; }

    static boolean isNoteWrite(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return ("POST".equals(request.getMethod()) && "/api/note/save".equals(path))
                || ("PUT".equals(request.getMethod()) && path.matches("/api/note/update/[0-9]+"));
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                               FilterChain chain) throws ServletException, IOException {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (!isNoteWrite(request) || auth == null || !auth.isAuthenticated()
                || auth instanceof AnonymousAuthenticationToken) {
            chain.doFilter(request, response); return;
        }
        try {
            var type = MediaType.parseMediaType(request.getContentType() == null ? "application/octet-stream" : request.getContentType());
            if ((!MediaType.APPLICATION_JSON.isCompatibleWith(type) && !type.getSubtype().endsWith("+json"))
                    || (type.getCharset() != null && !StandardCharsets.UTF_8.equals(type.getCharset()))) {
                error(response, 415, "NOTE_ENCODING_UNSUPPORTED", "노트 입력 형식을 확인해주세요."); return;
            }
        } catch (IllegalArgumentException error) { invalid(response); return; }
        if (request.getContentLengthLong() > REQUEST_BYTES) { tooLarge(response); return; }
        byte[] body = request.getInputStream().readNBytes(REQUEST_BYTES + 1);
        if (body.length > REQUEST_BYTES) { tooLarge(response); return; }
        try {
            // Reject malformed UTF-8 instead of replacing user text or allowing an
            // alternate decoder to bypass the content-byte contract.
            String json = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(body)).toString();
            JsonNode node;
            try (var parser = mapper.getFactory().createParser(json)) {
                parser.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
                node = mapper.readTree(parser);
                if (node == null || !node.isObject() || parser.nextToken() != null) {
                    invalid(response); return;
                }
            }
            var content = node.get("content");
            if (content != null && content.isTextual()
                    && content.textValue().getBytes(StandardCharsets.UTF_8).length > CONTENT_BYTES) {
                tooLarge(response); return;
            }
        } catch (com.fasterxml.jackson.core.JsonProcessingException | CharacterCodingException error) {
            invalid(response); return; // Never log or echo note content/parser messages.
        }
        chain.doFilter(new CachedBody(request, body), response);
    }

    private void invalid(HttpServletResponse response) throws IOException {
        error(response, 400, "NOTE_REQUEST_INVALID", "노트 입력 형식을 확인해주세요.");
    }
    private void tooLarge(HttpServletResponse response) throws IOException {
        error(response, 413, "NOTE_REQUEST_TOO_LARGE", "노트가 너무 길어요. 내용을 나누어 저장해주세요.");
    }
    private void error(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status); response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        mapper.writeValue(response.getWriter(), Map.of("status", status, "errorCode", code, "message", message));
    }

    private static final class CachedBody extends HttpServletRequestWrapper {
        private final byte[] body;
        CachedBody(HttpServletRequest request, byte[] body) { super(request); this.body = body; }
        @Override public int getContentLength() { return body.length; }
        @Override public long getContentLengthLong() { return body.length; }
        @Override public BufferedReader getReader() { return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8)); }
        @Override public ServletInputStream getInputStream() {
            var input = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override public int read() { return input.read(); }
                @Override public int read(byte[] bytes, int offset, int length) { return input.read(bytes, offset, length); }
                @Override public boolean isFinished() { return input.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener listener) {
                    // These MVC JSON routes use blocking I/O; no partial async stream.
                    throw new IllegalStateException("NOTE_BODY_BLOCKING_ONLY");
                }
            };
        }
    }
}
