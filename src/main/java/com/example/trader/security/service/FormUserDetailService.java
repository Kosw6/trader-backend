package com.example.trader.security.service;

import com.example.trader.dto.ResponseUserDto;
import com.example.trader.entity.User;
import com.example.trader.repository.UserRepository;
import com.example.trader.security.details.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

//일반적인 로그인시 loginId+Password
@Service
@RequiredArgsConstructor
public class FormUserDetailService implements UserDetailsService {
    private final UserRepository userRepository;

    //회원Dto와 권한을 가져옴
    @Override
    public UserDetails loadUserByUsername(String loginId) throws UsernameNotFoundException {
        User user = userRepository.findByLoginId(loginId).get();
        if (user == null) {
            //TODO:클라이언트가 따로 처리하도록 하기
            throw new UsernameNotFoundException("No user found with loginId: " + loginId);
        }
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        SimpleGrantedAuthority authority = new SimpleGrantedAuthority(user.getRole().getRoleName());
        authorities.add(authority);
        ResponseUserDto userDto = ResponseUserDto.of(user);

        return new UserContext(userDto,authorities);
    }
}
