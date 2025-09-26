package com.example.trader.dto.map;

import java.util.List;

public record ResponseGraphDto(Long pageId,List<ResponseNodeDto> nodes, List<ResponseEdgeDto> edges) {}
