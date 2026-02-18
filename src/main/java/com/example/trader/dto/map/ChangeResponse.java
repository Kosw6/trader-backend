package com.example.trader.dto.map;

import java.util.List;

public record ChangeResponse(
        List<DirectoryWithPagesDto> affectedDirectories
) {
    public static ChangeResponse of(List<DirectoryWithPagesDto> list) {
        return new ChangeResponse(list);
    }
}
