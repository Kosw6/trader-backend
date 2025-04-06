package com.example.trader.security.details;

import com.example.trader.dto.ResponseUserDto;
import lombok.Data;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Data
public class UserContext implements UserDetails {
    private ResponseUserDto userDto;
    private List<SimpleGrantedAuthority> roles;

    public UserContext(ResponseUserDto userDto, List<SimpleGrantedAuthority> roles) {
        this.userDto = userDto;
        this.roles = roles;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles;
    }

    @Override
    public String getPassword() {//비밀번호
        return userDto.getPassword();
    }

    @Override
    public String getUsername() {//로그인시 사용한 로그인 아이디
        return userDto.getLoginId();
    }

}
