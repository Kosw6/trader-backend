package com.example.trader.exception;

public class InvalidNodeChangeRequestException extends RuntimeException {
    private final NodeChangeErrorCode errorCode;

    public InvalidNodeChangeRequestException(NodeChangeErrorCode errorCode) {
        super(errorCode.message());
        this.errorCode = errorCode;
    }

    public InvalidNodeChangeRequestException(NodeChangeErrorCode errorCode, String detail) {
        super(errorCode.message() + " " + detail);
        this.errorCode = errorCode;
    }

    public NodeChangeErrorCode getErrorCode() {
        return errorCode;
    }
}
