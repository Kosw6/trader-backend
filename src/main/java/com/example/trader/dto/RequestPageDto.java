// RequestPageDto.java
package com.example.trader.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RequestPageDto {
    @NotBlank(message = "제목은 필수입니다.")
    private String title;
    private Long directoryId;
}
