package com.example.trader.entity;

import lombok.Getter;

@Getter
public enum NotificationRelatedType {
    JOIN_REQUEST("팀 합류 요청"),
    TEAM("팀"),
    NODE("노드"),
    NOTE("노트"),
    COMMENT("댓글");

    //상세 설명
    private final String desc;

    NotificationRelatedType(String desc) {
        this.desc = desc;
    }
}
