package com.example.trader.exception;

import com.example.trader.dto.canvas.ConflictResult;
import com.example.trader.httpresponse.BaseResponseStatus;
import lombok.Getter;

@Getter
public class NodeConflictException extends BaseException {

    private final ConflictResult result;

    public NodeConflictException(ConflictResult result) {
        super(BaseResponseStatus.NODE_EDIT_CONFLICT,
                "Node edit conflict detected: " + result.conflictingFields());
        this.result = result;
    }
}