package com.example.trader.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/** API Key는 소지자 자격이다. 현재 사용자의 자료 권한은 조회 서비스가 다시 검사한다. */
public final class AiServiceAuthFilter extends OncePerRequestFilter {
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(AiServiceAuthFilter.class);
    private static final Pattern SOURCE = Pattern.compile("/internal/ai/(indexing|retrieval)/sources/(NOTE|NODE|EDGE)/[1-9][0-9]{0,18}");
    private static final Pattern RELATIONS = Pattern.compile("/internal/ai/(indexing|retrieval)/nodes/[1-9][0-9]{0,18}/relations");
    private static final Pattern INITIAL = Pattern.compile("/internal/ai/indexing/initial-runs/[0-9a-fA-F-]{36}/sources");
    private static final Pattern INITIAL_COMPLETION = Pattern.compile("/internal/ai/indexing/initial-runs/[0-9a-fA-F-]{36}/completion");
    private final boolean enabled;
    private final String retrievalKey, indexingKey;
    private final int executionSeconds, skewSeconds;
    private final Clock clock;
    private final ObjectMapper mapper = new ObjectMapper();

    public AiServiceAuthFilter(boolean enabled, String retrievalKey, String indexingKey,
                               int executionSeconds, int skewSeconds, Clock clock) {
        if (executionSeconds < 1 || executionSeconds > 600 || skewSeconds < 0 || skewSeconds > 30)
            throw new IllegalArgumentException("AI 실행 시간 설정이 올바르지 않습니다.");
        if (enabled && (!validKey(retrievalKey) || !validKey(indexingKey) || retrievalKey.equals(indexingKey)))
            throw new IllegalArgumentException("서로 다른 AI 조회용·색인용 서비스 키가 필요합니다.");
        this.enabled = enabled; this.retrievalKey = retrievalKey; this.indexingKey = indexingKey;
        this.executionSeconds = executionSeconds; this.skewSeconds = skewSeconds; this.clock = clock;
    }
    private static boolean validKey(String value) { return value != null && value.matches("[A-Za-z0-9_-]{43,128}"); }
    private static boolean matches(String expected, String actual) {
        return actual != null && actual.length() <= 256 && MessageDigest.isEqual(
                ("Bearer " + expected).getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }
    private static String header(HttpServletRequest request, String name) {
        var values = Collections.list(request.getHeaders(name));
        if (values.size() > 1) throw AiApiException.invalid();
        return values.isEmpty() ? null : values.get(0);
    }
    @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        String requestId = UUID.randomUUID().toString();
        try {
            if (!enabled) throw new AiApiException(503, "AI_INTERNAL_DISABLED");
            String path = req.getRequestURI();
            boolean index = path.startsWith("/internal/ai/indexing/");
            String key = header(req, "Authorization");
            if (!matches(retrievalKey, key) && !matches(indexingKey, key))
                throw new AiApiException(401, "SERVICE_AUTH_REQUIRED");
            boolean readAllowed="GET".equals(req.getMethod()) && (SOURCE.matcher(path).matches() || RELATIONS.matcher(path).matches()
                    || INITIAL.matcher(path).matches() || path.equals("/internal/ai/retrieval/evidence-checks"));
            boolean reportAllowed="POST".equals(req.getMethod()) && INITIAL_COMPLETION.matcher(path).matches()
                    && req.getContentLengthLong()>0 && req.getContentLengthLong()<=8192;
            if (!readAllowed && !reportAllowed)
                throw new AiApiException(403, "SERVICE_OPERATION_FORBIDDEN");
            if (!matches(index ? indexingKey : retrievalKey, key))
                throw new AiApiException(403, "SERVICE_OPERATION_FORBIDDEN");
            String target = path + (req.getQueryString() == null ? "" : "?" + req.getQueryString());
            if (target.getBytes(StandardCharsets.UTF_8).length > 6144) throw new AiApiException(414, "REQUEST_TARGET_TOO_LARGE");
            if (req.getParameterMap().values().stream().anyMatch(v -> v.length != 1)) throw AiApiException.invalid();
            String providedId = header(req, "X-Request-Id");
            if (providedId != null) {
                if (!providedId.matches("[A-Za-z0-9_-]{1,64}")) throw AiApiException.invalid();
                requestId = providedId;
            }
            Long userId = null; Instant accepted = null, deadline = null;
            if (index) {
                if (header(req, "X-AI-User-Id") != null || header(req, "X-AI-Accepted-At") != null
                        || header(req, "X-AI-Deadline-At") != null) throw AiApiException.invalid();
            } else {
                try {
                    String raw = header(req, "X-AI-User-Id");
                    if (raw == null || !raw.matches("[1-9][0-9]{0,18}")) throw AiApiException.invalid();
                    userId = Long.parseLong(raw);
                    accepted = Instant.parse(header(req, "X-AI-Accepted-At"));
                    deadline = Instant.parse(header(req, "X-AI-Deadline-At"));
                    Instant now = clock.instant();
                    if (accepted.isAfter(now.plusSeconds(skewSeconds)) || !deadline.isAfter(accepted)
                            || deadline.isAfter(accepted.plusSeconds(executionSeconds))) throw AiApiException.invalid();
                    if (!now.isBefore(deadline)) throw new AiApiException(408, "EXECUTION_DEADLINE_EXCEEDED");
                } catch (AiApiException e) { throw e; }
                catch (RuntimeException e) { throw AiApiException.invalid(); }
            }
            req.setAttribute(AiRequestContext.ATTRIBUTE, new AiRequestContext(index, userId, requestId, accepted, deadline));
        } catch (AiApiException e) {
            log.info("ai_read_rejected requestId={} code={}",requestId,e.code);
            res.setStatus(e.status); res.setContentType("application/json"); res.setCharacterEncoding("UTF-8");
            res.setHeader("Cache-Control", "no-store");
            mapper.writeValue(res.getOutputStream(), Map.of("code", e.code, "message", e.getMessage(), "requestId", requestId));
            return;
        }
        res.setHeader("Cache-Control", "no-store");
        res.setHeader("X-Request-Id", requestId);
        try { chain.doFilter(req, res); }
        finally {
            var context=(AiRequestContext)req.getAttribute(AiRequestContext.ATTRIBUTE);
            // 쿼리에는 evidence/커서가 있으므로 전체 URL·헤더·원문은 기록하지 않는다.
            log.info("ai_read service={} userId={} requestId={} target={} status={}",
                    context.indexing()?"indexing":"retrieval",context.userId(),requestId,req.getRequestURI(),res.getStatus());
        }
    }
}
