package com.example.trader.dto.map;

import java.util.List;

public record ResponseGraphDto(Long pageId,List<ResponseNodeSummaryDto> nodes, List<ResponseEdgeDto> edges) {}
