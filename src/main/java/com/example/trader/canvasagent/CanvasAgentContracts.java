package com.example.trader.canvasagent;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;

public final class CanvasAgentContracts {
    private CanvasAgentContracts() {}

    public record Claim(@NotNull UUID requestId,
                        @NotNull @Pattern(regexp="[0-9a-f]{64}") String goalHash,
                        @NotNull @Pattern(regexp="[0-9a-f]{64}") String baseFingerprint) {}
    public record NewNode(@NotBlank @Pattern(regexp="n[1-9][0-9]?") String key,
                          @NotBlank @Size(max=120) String subject,
                          @NotNull @Size(max=6000) String content,
                          @NotNull @Pattern(regexp="[A-Z0-9.\\-]{0,20}") String symb,
                          @Min(-100000) @Max(100000) double x,
                          @Min(-100000) @Max(100000) double y) {}
    public record NewEdge(@NotBlank @Pattern(regexp="(?:n[1-9][0-9]?|[1-9][0-9]{0,18})") String source,
                          @NotBlank @Pattern(regexp="(?:n[1-9][0-9]?|[1-9][0-9]{0,18})") String target,
                          @NotNull @Size(max=120) String label) {}
    public record NodeEdit(@Min(1) long nodeId,
                           @Size(max=120) String subject,
                           @Size(max=6000) String content,
                           @Min(-100000) @Max(100000) Double x,
                           @Min(-100000) @Max(100000) Double y) {}
    public record Apply(@NotNull UUID requestId,
                        @NotNull @Pattern(regexp="[0-9a-f]{64}") String baseFingerprint,
                        @NotNull @Size(max=6) List<@NotNull @Valid NewNode> nodes,
                        @NotNull @Size(max=8) List<@NotNull @Valid NewEdge> edges,
                        @Size(max=6) List<@NotNull @Valid NodeEdit> edits) {
        public Apply { edits=edits==null?List.of():List.copyOf(edits); }
        public Apply(UUID id,String fingerprint,List<NewNode> nodes,List<NewEdge> edges) {
            this(id,fingerprint,nodes,edges,List.of());
        }
    }
}
