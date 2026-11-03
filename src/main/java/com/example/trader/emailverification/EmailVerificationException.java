package com.example.trader.emailverification;

import org.springframework.http.HttpStatus;

public class EmailVerificationException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    public EmailVerificationException(HttpStatus status, String code) {
        super(code); // Never include an address, OTP, token, AWS message or Redis payload.
        this.status = status;
        this.code = code;
    }
    public HttpStatus status() { return status; }
    public String code() { return code; }
}
