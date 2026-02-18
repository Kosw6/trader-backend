// EdgeRequestDto.java
package com.example.trader.dto.map;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RequestEdgeDto {
    //시작노드
    private Long sourceId;
    //종단노드
    private Long targetId;
    private String type;
    private String label;
    //시작핸들방향
    private String sourceHandle;
    //종단핸들방향
    private String targetHandle;
    private String variant;
    private boolean animated;
    private String stroke;
    private Integer strokeWidth;
}