package com.example.trader.security.service;

import com.example.trader.dto.ResponseUserDto;
import com.example.trader.entity.User;
import com.example.trader.repository.UserRepository;
import com.example.trader.security.details.UserContext;
import com.example.trader.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

//일반적인 로그인시 loginId+Password
@Slf4j
@Service
@RequiredArgsConstructor
public class FormUserDetailService implements UserDetailsService {
//    private final UserRepository userRepository;
    private final UserService userService;
    //회원Dto와 권한을 가져옴
    @Override
    public UserDetails loadUserByUsername(String loginId) throws UsernameNotFoundException {
        User user;
        try {
            user = userService.findUserByLoginId(loginId);
        } catch (NoSuchElementException missing) {
            // The repository's Optional is empty for unknown/deleted accounts.
            // Preserve the same authentication failure contract as a bad password.
            throw new UsernameNotFoundException("Invalid credentials");
        }
        if (user == null) {
            //TODO:클라이언트가 따로 처리하도록 하기
            throw new UsernameNotFoundException("Invalid credentials");
        }
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        SimpleGrantedAuthority authority = new SimpleGrantedAuthority(user.getRole().getRoleName());
        authorities.add(authority);
        ResponseUserDto userDto = ResponseUserDto.of(user);

        return new UserContext(userDto,authorities);
    }
}
