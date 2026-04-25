package com.example.trader.exception;

import com.example.trader.dto.canvas.ConflictResult;
import lombok.Getter;

/**
 * 노드 편집 충돌 발생 시 throw.
 * GlobalExceptionHandler → 409 Conflict 응답으로 변환됨.
 */
@Getter
public class NodeConflictException extends RuntimeException {

    private final ConflictResult result;

    public NodeConflictException(ConflictResult result) {
        super("Node edit conflict detected: " + result.conflictingFields());
        this.result = result;
    }
}
