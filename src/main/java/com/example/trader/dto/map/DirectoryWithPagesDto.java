package com.example.trader.dto.map;

import java.util.List;

public record DirectoryWithPagesDto(
        ResponseDirectoryDto directory,
        List<ResponsePageDto> pages
) {}
