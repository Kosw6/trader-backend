// RequestDirectoryDto.java
package com.example.trader.dto.map;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RequestDirectoryDto {
    @NotBlank(message = "디렉토리 이름을 작성해주세요")
    private String name;
    private Long parentId; // 상위 디렉토리 ID (nullable), 수정할때
}
