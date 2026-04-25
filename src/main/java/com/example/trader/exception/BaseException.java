package com.example.trader.exception;

import com.example.trader.httpresponse.BaseResponseStatus;
import lombok.Getter;

@Getter
public class BaseException extends RuntimeException {

    private final BaseResponseStatus status;

    public BaseException(BaseResponseStatus status) {
        super(status.getMessage());
        this.status = status;
    }

    public BaseException(BaseResponseStatus status, String customMessage) {
        super(customMessage);
        this.status = status;
    }
}