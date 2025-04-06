package com.example.trader.dto;

import com.example.trader.entity.User;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResponseUserDto {//Authentication 인증객체로 넘길 dto
    private Long id;
    private String username;//닉네임
    private String loginId;//로그인시에 사용할 아이디
    private String password;//비밀번호
    private String email;

    public static ResponseUserDto of(User user){
        return new ResponseUserDto(user.getId(),
                user.getUsername(),
                user.getLoginId(),
                user.getPassword(),
                user.getEmail());
    }
}
