package com.example.trader.httpresponse;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum BaseResponseStatus {

    SUCCESS(true, HttpStatus.OK.value(), "요청에 성공하였습니다.", "common", "SUCCESS", Severity.LOW),
    NO_CONTENT(false, HttpStatus.NO_CONTENT.value(), "요청에 성공하였습니다.", "common", "NO_CONTENT", Severity.LOW),

    // 4xx : client error
    FAIL_TOKEN_AUTHORIZATION(false, HttpStatus.UNAUTHORIZED.value(), "토큰 인증에 실패하였습니다.", "auth", "FAIL_TOKEN_AUTHORIZATION", Severity.MEDIUM),
    FAIL_AUTHENTICATE(false, HttpStatus.UNAUTHORIZED.value(), "인증에 실패했습니다.", "auth", "FAIL_AUTHENTICATE", Severity.MEDIUM),
    EXIST_EMAIL(false, HttpStatus.CONFLICT.value(), "이미 존재하는 이메일입니다.", "user", "EXIST_EMAIL", Severity.LOW),
    EXIST_NICKNAME(false, HttpStatus.CONFLICT.value(), "이미 존재하는 사용자명입니다.", "user", "EXIST_NICKNAME", Severity.LOW),
    EXIST_LOGIN_ID(false, HttpStatus.CONFLICT.value(), "이미 존재하는 아이디입니다.", "user", "EXIST_LOGIN_ID", Severity.LOW),
    TEAM_OWNER_CONFLICT(false, HttpStatus.CONFLICT.value(), "팀의 오너는 중복될 수 없습니다.", "team", "TEAM_OWNER_CONFLICT", Severity.MEDIUM),

    // DB에 존재X
    USER_NOT_FOUND(false, HttpStatus.NOT_FOUND.value(), "존재하지 않는 회원입니다.", "user", "USER_NOT_FOUND", Severity.LOW),
    TEAM_NOT_FOUND(false, HttpStatus.NOT_FOUND.value(), "존재하지 않는 그룹입니다.", "team", "TEAM_NOT_FOUND", Severity.LOW),
    NOTE_NOT_FOUND(false, HttpStatus.NOT_FOUND.value(), "존재하지 않는 노트입니다.", "note", "NOTE_NOT_FOUND", Severity.LOW),
    DIRECTORY_NOT_FOUND(false, HttpStatus.NOT_FOUND.value(), "존재하지 않는 디렉토리입니다.", "directory", "DIRECTORY_NOT_FOUND", Severity.LOW),
    PAGE_NOT_FOUND(false, HttpStatus.NOT_FOUND.value(), "존재하지 않는 페이지입니다.", "page", "PAGE_NOT_FOUND", Severity.LOW),
    NODE_NOT_FOUND(false, HttpStatus.NOT_FOUND.value(), "존재하지 않는 노드입니다.", "canvas", "NODE_NOT_FOUND", Severity.LOW),
    EDGE_NOT_FOUND(false, HttpStatus.NOT_FOUND.value(), "존재하지 않는 엣지입니다.", "canvas", "EDGE_NOT_FOUND", Severity.LOW),
    JOIN_REQUEST_NOT_FOUND(false, HttpStatus.NOT_FOUND.value(), "대기중인 합류 요청이 없습니다.", "team", "JOIN_REQUEST_NOT_FOUND", Severity.LOW),
    HTTP_METHOD_ERROR(false, HttpStatus.METHOD_NOT_ALLOWED.value(), "http요청 메서드가 올바르지 않습니다.", "common", "HTTP_METHOD_ERROR", Severity.LOW),

    // 존재하지만 문제있을때
    INVALID_USER(false, HttpStatus.NOT_FOUND.value(), "유효하지 않은 회원입니다.", "user", "INVALID_USER", Severity.LOW),
    INVALID_NOTE(false, HttpStatus.NOT_FOUND.value(), "존재하지 않는 노트입니다.", "note", "INVALID_NOTE", Severity.LOW),
    INVALID_TEAM(false, HttpStatus.NOT_FOUND.value(), "존재하지 않는 팀입니다.", "team", "INVALID_TEAM", Severity.LOW),
    INVALID_DIRECTORY(false, HttpStatus.NOT_FOUND.value(), "존재하지 않는 디렉토리입니다.", "directory", "INVALID_DIRECTORY", Severity.LOW),
    INVALID_PAGE(false, HttpStatus.NOT_FOUND.value(), "존재하지 않는 페이지입니다.", "page", "INVALID_PAGE", Severity.LOW),
    INVALID_NODE(false, HttpStatus.NOT_FOUND.value(), "존재하지 않는 노드입니다.", "canvas", "INVALID_NODE", Severity.LOW),
    INVALID_EDGE(false, HttpStatus.NOT_FOUND.value(), "존재하지 않는 엣지입니다.", "canvas", "INVALID_EDGE", Severity.LOW),

    INVALID_REQUEST(false, HttpStatus.BAD_REQUEST.value(), "유효하지 않은 요청입니다.", "common", "INVALID_REQUEST", Severity.LOW),

    INVALID_JWT_TOKEN(false, HttpStatus.UNAUTHORIZED.value(), "검증하는 JWT토큰이 유효하지 않습니다. 재발급해주세요", "auth", "INVALID_JWT_TOKEN", Severity.MEDIUM),
    ACCESS_TOKEN_EXPIRED(false, HttpStatus.UNAUTHORIZED.value(), "만료된 AcessToken입니다", "auth", "ACCESS_TOKEN_EXPIRED", Severity.MEDIUM),
    ACCESS_DENIED(false, HttpStatus.UNAUTHORIZED.value(), "권한이 유효하지 않습니다.", "auth", "ACCESS_DENIED", Severity.MEDIUM),
    ALREADY_TEAM_MEMBER(false, HttpStatus.CONFLICT.value(), "이미 팀에 합류를 했습니다.", "team", "ALREADY_TEAM_MEMBER", Severity.LOW),
    TEAM_ROLE_CHANGE_DENIED(false, HttpStatus.FORBIDDEN.value(), "팀 역할 변경을 할 수 없습니다.", "team", "TEAM_ROLE_CHANGE_DENIED", Severity.MEDIUM),
    NODE_LOCKED(false, HttpStatus.CONFLICT.value(), "다른 유저가 해당 노드를 이동 중입니다.", "canvas", "NODE_LOCKED", Severity.MEDIUM),
    NODE_EDIT_CONFLICT(false, HttpStatus.CONFLICT.value(), "다른 유저가 같은 필드를 수정했습니다. 변경 내용을 확인하세요.", "canvas", "NODE_EDIT_CONFLICT", Severity.MEDIUM),

    // 5xx : server error
    DATABASE_INSERT_ERROR(false, HttpStatus.INTERNAL_SERVER_ERROR.value(), "데이터베이스 입력에 실패했습니다.", "database", "DATABASE_INSERT_ERROR", Severity.HIGH),
    FAIL_IMAGE_CONVERT(false, HttpStatus.INTERNAL_SERVER_ERROR.value(), "Multipart 파일 전환에 실패했습니다.", "file", "FAIL_IMAGE_CONVERT", Severity.MEDIUM),
    FAIL_CREATE_TEAM(false, HttpStatus.INTERNAL_SERVER_ERROR.value(), "팀 코드 생성에 실패했습니다. 잠시 후 다시 시도해주세요.", "team", "FAIL_CREATE_TEAM", Severity.HIGH),
    INTERNAL_SERVER_ERROR(false, HttpStatus.INTERNAL_SERVER_ERROR.value(), "서버 오류가 발생했습니다.", "common", "INTERNAL_SERVER_ERROR", Severity.HIGH);

    private final boolean isSuccess;
    private final int code;
    private final String message;

    private final String domain;
    private final String errorCode;
    private final Severity severity;

    BaseResponseStatus(
            boolean isSuccess,
            int code,
            String message,
            String domain,
            String errorCode,
            Severity severity
    ) {
        this.isSuccess = isSuccess;
        this.code = code;
        this.message = message;
        this.domain = domain;
        this.errorCode = errorCode;
        this.severity = severity;
    }

    public enum Severity {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }
}