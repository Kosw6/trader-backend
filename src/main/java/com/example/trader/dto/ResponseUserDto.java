package com.example.trader.dto;

import com.example.trader.entity.Gender;
import com.example.trader.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResponseUserDto {//Authentication 인증객체로 넘길 dto
    private Long id;
    private String username;//닉네임
    private String loginId;//로그인시에 사용할 아이디
    private String password;//비밀번호
    private String email;
    private String gender;
    private String nickName;
    private String role;


    public static ResponseUserDto of(User user){
        return ResponseUserDto.builder().email(user.getEmail())
                .gender(user.getGender().getGenderName()).
                id(user.getId()).
                nickName(user.getNickName()).
                loginId(user.getLoginId()).
                password(user.getPassword()).
                role(user.getRole().getRoleName()).
                username(user.getUsername()).build();
    }
}
