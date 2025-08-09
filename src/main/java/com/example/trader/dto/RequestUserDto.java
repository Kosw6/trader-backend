package com.example.trader.dto;

public record RequestUserDto(Long id,
                             String email,
                             String username,
                             Integer age,
                             String loginId,
                             String password,
                             String gender,
                             String nickName) {
}
