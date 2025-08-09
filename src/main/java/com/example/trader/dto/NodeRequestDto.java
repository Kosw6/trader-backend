// NodeRequestDto.java (생성/수정 요청용)
package com.example.trader.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NodeRequestDto {
    private double x;
    private double y;
    private String subject;
    private String content;
    private String symb;
    private Long noteId; // note와 연관관계가 있을 경우
    private Long pageId;
    //TODO:수정
}