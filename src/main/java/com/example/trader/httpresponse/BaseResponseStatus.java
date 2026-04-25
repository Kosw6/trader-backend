package com.example.trader.httpresponse;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum BaseResponseStatus {

    /**
     * 성공 코드 2xx
     * 코드의 원활한 이해을 위해 code는 숫자가 아닌 아래 형태로 입력해주세요.
     */
    SUCCESS(true, HttpStatus.OK.value(), "요청에 성공하였습니다."),
    NO_CONTENT(false, HttpStatus.NO_CONTENT.value(), "요청에 성공하였습니다."),


    // 4xx : client error
    FAIL_TOKEN_AUTHORIZATION(false, HttpStatus.UNAUTHORIZED.value(), "토큰 인증에 실패하였습니다."),
    FAIL_AUTHENTICATE(false, HttpStatus.UNAUTHORIZED.value(), "인증에 실패했습니다."),
    EXIST_EMAIL(false, HttpStatus.CONFLICT.value(), "이미 존재하는 이메일입니다."),
    EXIST_NICKNAME(false, HttpStatus.CONFLICT.value(), "이미 존재하는 사용자명입니다."),
    EXIST_LOGIN_ID(false, HttpStatus.CONFLICT.value(), "이미 존재하는 아이디입니다."),
    TEAM_OWNER_CONFLICT(false, HttpStatus.CONFLICT.value(), "팀의 오너는 중복될 수 없습니다."),

    //DB에 존재X
    USER_NOT_FOUND(false, HttpStatus.NOT_FOUND.value(), "존재하지 않는 회원입니다."),
    TEAM_NOT_FOUND(false, HttpStatus.NOT_FOUND.value(), "존재하지 않는 그룹입니다."),
    NOTE_NOT_FOUND(false, HttpStatus.NOT_FOUND.value(), "존재하지 않는 노트입니다."),
    DIRECTORY_NOT_FOUND(false,HttpStatus.NOT_FOUND.value(),"존재하지 않는 디렉토리입니다."),
    PAGE_NOT_FOUND(false,HttpStatus.NOT_FOUND.value(),"존재하지 않는 페이지입니다."),
    NODE_NOT_FOUND(false,HttpStatus.NOT_FOUND.value(),"존재하지 않는 노드입니다."),
    EDGE_NOT_FOUND(false,HttpStatus.NOT_FOUND.value(),"존재하지 않는 엣지입니다."),
    JOIN_REQUEST_NOT_FOUND(false,HttpStatus.NOT_FOUND.value(),"대기중인 합류 요청이 없습니다."),
    HTTP_METHOD_ERROR(false, HttpStatus.METHOD_NOT_ALLOWED.value(), "http요청 메서드가 올바르지 않습니다."),

    //존재하지만 문제있을때
    INVALID_USER(false, HttpStatus.NOT_FOUND.value(), "유효하지 않은 회원입니다."),
    INVALID_NOTE(false, HttpStatus.NOT_FOUND.value(), "존재하지 않는 노트입니다."),
    INVALID_TEAM(false, HttpStatus.NOT_FOUND.value(), "존재하지 않는 팀입니다."),
    INVALID_DIRECTORY(false,HttpStatus.NOT_FOUND.value(),"존재하지 않는 디렉토리입니다."),
    INVALID_PAGE(false,HttpStatus.NOT_FOUND.value(),"존재하지 않는 페이지입니다."),
    INVALID_NODE(false,HttpStatus.NOT_FOUND.value(),"존재하지 않는 노드입니다."),
    INVALID_EDGE(false,HttpStatus.NOT_FOUND.value(),"존재하지 않는 엣지입니다."),

    INVALID_REQUEST(false, HttpStatus.BAD_REQUEST.value(), "유효하지 않은 요청입니다."),

    INVALID_JWT_TOKEN(false,HttpStatus.UNAUTHORIZED.value(),"검증하는 JWT토큰이 유효하지 않습니다. 재발급해주세요"),
    ACCESS_TOKEN_EXPIRED(false,HttpStatus.UNAUTHORIZED.value(),"만료된 AcessToken입니다"),
    ACCESS_DENIED(false,HttpStatus.UNAUTHORIZED.value(),"권한이 유효하지 않습니다."),
    ALREADY_TEAM_MEMBER(false,HttpStatus.CONFLICT.value(), "이미 팀에 합류를 했습니다."),
    TEAM_ROLE_CHANGE_DENIED(false,HttpStatus.FORBIDDEN.value(), "팀 역할 변경을 할 수 없습니다."),
    NODE_LOCKED(false, HttpStatus.CONFLICT.value(), "다른 유저가 해당 노드를 이동 중입니다."),
    NODE_EDIT_CONFLICT(false, HttpStatus.CONFLICT.value(), "다른 유저가 같은 필드를 수정했습니다. 변경 내용을 확인하세요."),

    // 5xx : server error
    DATABASE_INSERT_ERROR(false, HttpStatus.INTERNAL_SERVER_ERROR.value(), "데이터베이스 입력에 실패했습니다."),
    FAIL_IMAGE_CONVERT(false, HttpStatus.INTERNAL_SERVER_ERROR.value(), "Multipart 파일 전환에 실패했습니다."),
    FAIL_CREATE_TEAM(false,HttpStatus.INTERNAL_SERVER_ERROR.value(),"팀 코드 생성에 실패했습니다. 잠시 후 다시 시도해주세요."),
    INTERNAL_SERVER_ERROR(false,HttpStatus.INTERNAL_SERVER_ERROR.value(),"서버 오류가 발생했습니다." );
    private final boolean isSuccess;
    private final int code;
    private final String message;

    /**
     * isSuccess : 요청의 성공 또는 실패
     * code : Http Status Code
     * message : 설명
     */

    BaseResponseStatus(boolean isSuccess, int code, String message) {
        this.isSuccess = isSuccess;
        this.code = code;
        this.message = message;
    }
}
