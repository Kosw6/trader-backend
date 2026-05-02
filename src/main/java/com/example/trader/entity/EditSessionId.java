package com.example.trader.entity;

import lombok.*;

import java.io.Serializable;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class EditSessionId implements Serializable { //복합 키 클래스

    private Long teamId;
    private Long graphId;
    private Long nodeId;
    private Long userId;
}