package com.example.trader.dto.map;

import java.util.List;

public record InitPayload(List<ResponseDirectoryDto> directory,
                          List<ResponsePageDto> pages) {

}
