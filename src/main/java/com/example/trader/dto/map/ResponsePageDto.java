// ResponsePageDto.java
package com.example.trader.dto.map;

import com.example.trader.entity.Edge;
import com.example.trader.entity.Node;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class ResponsePageDto {
    private Long id;
    private String title;
    private Long directoryId;
}
