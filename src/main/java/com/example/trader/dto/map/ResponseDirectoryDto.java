// ResponseDirectoryDto.java
package com.example.trader.dto.map;

import com.example.trader.entity.Directory;
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
    public static ResponseDirectoryDto ofDto(Directory dir){
        return ResponseDirectoryDto.builder()
                .id(dir.getId())
                .name(dir.getName())
                .parentId(dir.getParent() != null ? dir.getParent().getId() : null)
                .build();

    }
}
