package com.example.trader.dto.map;

public record UpdateDirReq(
        String name,
        Long parentId
) {
    public boolean parentIdChanged() { return true; } // 필요 시 커스텀 로직
}
