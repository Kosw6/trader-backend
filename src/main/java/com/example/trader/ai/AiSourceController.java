package com.example.trader.ai;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/internal/ai")
public class AiSourceController {
    private final AiSourceReader reader;
    private final AiCursorCodec cursor;
    private final AiReadLimits limits;
    public AiSourceController(AiSourceReader reader, AiCursorCodec cursor,AiReadLimits limits) { this.reader = reader; this.cursor = cursor; this.limits=limits; }
    private AiRequestContext context(HttpServletRequest request) {
        var value = request.getAttribute(AiRequestContext.ATTRIBUTE);
        if (!(value instanceof AiRequestContext context)) throw new AiApiException(401, "SERVICE_AUTH_REQUIRED");
        return context;
    }
    private AiScope scope(HttpServletRequest request) {
        return AiScope.of(context(request), request.getParameter("scopeType"), request.getParameter("teamId"));
    }
    private void parameters(HttpServletRequest request, String... allowed) {
        if (!Set.of(allowed).containsAll(request.getParameterMap().keySet())) throw AiApiException.invalid();
    }
    private ResponseEntity<byte[]> response(Object result, HttpServletRequest request) {
        byte[] data = AiCanonicalJson.bytes(result);
        if (data.length > limits.sourceBytes()) throw new AiApiException(413, "SOURCE_RESPONSE_TOO_LARGE");
        return ResponseEntity.ok().header("Content-Type", "application/json;charset=UTF-8")
                .header("Cache-Control", "no-store").header("X-Request-Id", context(request).requestId()).body(data);
    }
    @GetMapping({"/retrieval/sources/{type}/{id}", "/indexing/sources/{type}/{id}"})
    public ResponseEntity<byte[]> source(@PathVariable String type, @PathVariable String id, HttpServletRequest request) {
        parameters(request, "expectedRevision", "scopeType", "teamId");
        String expected = request.getParameter("expectedRevision");
        if (context(request).indexing() && expected == null) throw AiApiException.invalid();
        return response(reader.source(type, AiScope.id(id), expected == null ? null : AiScope.id(expected), scope(request)), request);
    }
    @GetMapping({"/retrieval/nodes/{id}/relations", "/indexing/nodes/{id}/relations"})
    public ResponseEntity<byte[]> relations(@PathVariable String id, HttpServletRequest request) {
        parameters(request, "expectedRevision", "scopeType", "teamId", "relationType", "pageSize", "cursor");
        String size = request.getParameter("pageSize");
        long pageSize = size == null ? limits.defaultPage() : AiScope.id(size);
        if (pageSize > limits.maxPage()) throw AiApiException.invalid();
        return response(reader.relations(AiScope.id(id), AiScope.id(request.getParameter("expectedRevision")),
                request.getParameter("relationType"), scope(request), (int) pageSize, request.getParameter("cursor"), cursor), request);
    }
    @GetMapping("/retrieval/evidence-checks")
    public ResponseEntity<byte[]> evidence(HttpServletRequest request) {
        parameters(request,"checks");
        return response(reader.evidence(AiEvidenceCheck.parse(request.getParameter("checks"),context(request))),request);
    }
}
