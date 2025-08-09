// RequestPageDto.java
package com.example.trader.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RequestPageDto {
    private String title;
    private String content;
    private Long directoryId;
}
