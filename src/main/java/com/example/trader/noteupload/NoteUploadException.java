package com.example.trader.noteupload;

import org.springframework.http.HttpStatus;

public class NoteUploadException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    public NoteUploadException(HttpStatus status, String code, String message) {
        super(message); this.status=status; this.code=code;
    }
    public HttpStatus status() { return status; }
    public String code() { return code; }
}
