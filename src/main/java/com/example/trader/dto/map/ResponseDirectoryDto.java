// ResponseDirectoryDto.java
package com.example.trader.dto.map;

import com.example.trader.entity.Page;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ResponseDirectoryDto {
    private Long id;
    private String name;
    private Long parentId;
}
