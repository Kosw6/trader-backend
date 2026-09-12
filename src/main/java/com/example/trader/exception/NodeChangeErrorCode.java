package com.example.trader.exception;

public enum NodeChangeErrorCode {
    CHANGE_FIELDS_REQUIRED("하나 이상의 변경 값을 요청 본문에 포함해야 합니다."),
    DIRTY_FIELDS_NOT_ALLOWED("개인 Node 수정에는 dirtyFields를 사용할 수 없습니다."),
    DIRTY_FIELDS_REQUIRED("dirtyFields가 필요합니다."),
    DIRTY_FIELDS_EMPTY("하나 이상의 변경 필드를 지정해야 합니다."),
    DIRTY_FIELD_VALUE_MISSING("변경 필드로 선언한 값이 요청 본문에 없습니다."),
    UNDECLARED_FIELD_VALUE("요청 본문의 변경 값이 dirtyFields에 선언되지 않았습니다."),
    UNSUPPORTED_DIRTY_FIELD("지원하지 않는 변경 필드입니다."),
    DUPLICATE_DIRTY_FIELD("dirtyFields에 중복된 필드가 있습니다."),
    NULL_VALUE_NOT_ALLOWED("해당 필드는 null로 변경할 수 없습니다."),
    EMPTY_SUBJECT_NOT_ALLOWED("subject는 빈 문자열일 수 없습니다."),
    BASE_VERSION_REQUIRED("팀 Node 수정에는 baseVersion이 필요합니다."),
    INVALID_BASE_VERSION("baseVersion은 0 이상이어야 합니다."),
    POSITION_FIELD_NOT_ALLOWED("내용 수정 API에서는 위치를 변경할 수 없습니다.");

    private final String message;

    NodeChangeErrorCode(String message) {
        this.message = message;
    }

    public String message() {
        return message;
    }
}
