// RequestDirectoryDto.java
package com.example.trader.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RequestDirectoryDto {
    private String name;
    private Long parentId; // 상위 디렉토리 ID (nullable)
}
