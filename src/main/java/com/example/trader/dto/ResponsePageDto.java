// ResponsePageDto.java
package com.example.trader.dto;

import com.example.trader.entity.Edge;
import com.example.trader.entity.Node;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ResponsePageDto {
    private Long id;
    private String title;
    private String content;
    private Long directoryId;
    private List<Node> nodeList;
    private List<Edge> edgeList;
}
