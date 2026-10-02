package com.example.trader.ai;

public class AiApiException extends RuntimeException {
    public final int status;
    public final String code;
    public AiApiException(int status, String code) {
        super("AI 자료 요청을 처리할 수 없습니다.");
        this.status = status;
        this.code = code;
    }
    public static AiApiException invalid() { return new AiApiException(400, "INVALID_REQUEST"); }
}
