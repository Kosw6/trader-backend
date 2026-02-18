package com.example.trader.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LoginRequest {
    @Schema(description = "로그인 아이디",example = "test1")
    private String loginId;
    @Schema(description = "비밀번호 아이디",example = "test1")
    private String password;
}
