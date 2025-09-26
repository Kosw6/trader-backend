package com.example.trader.service;

import com.example.trader.entity.Role;
import com.example.trader.entity.User;
import com.example.trader.exception.BaseException;
import jakarta.transaction.Transactional;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.junit.jupiter.api.BeforeEach;

import static org.assertj.core.api.Assertions.*;

@Transactional
@SpringBootTest
class UserServiceTest {

    @Autowired
    private UserService userService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .email("testUser2@example.com")
                .username("Test User")
                .loginId("testID_2")
                .password("testPW_2")
                .nickName("test1")
                .role(Role.USER)
                .build();
    }

    @Test
    void findUserByUserId() {
        User userByUserId = userService.findUserByUserId(1L);
        assertThat(1L).isEqualTo(userByUserId.getId());
    }

    @Test
    void createUser() {
        Long userId = userService.createUser(testUser);
        assertThat(testUser.getLoginId()).isEqualTo(userService.findUserByUserId(userId).getLoginId());
    }


    @Test
    void deleteUser_정상삭제_이후조회시_예외() {
        // given
        User testUser = User.builder()
                .email("delete@test.com")
                .username("Delete Me")
                .loginId("delete@test.com")
                .password("pw")
                .nickName("del")
                .role(Role.USER)
                .build();
        Long userId = userService.createUser(testUser);

        // when
        userService.deleteUser(userId);

        // then (삭제된 ID 재조회 시 예외가 나야 함 — 너희 서비스 구현에 맞춘 예외 타입으로 바꿔!)
        assertThatThrownBy(() -> userService.findUserByUserId(userId))
                // 예: .isInstanceOf(UserNotFoundException.class);
                .isInstanceOf(Exception.class); // 임시로 넓게
    }

    @Test
    void deleteUser_없는ID_예외() {
        // then (없는 ID 삭제 시 — 스프링 데이터 JPA 기본은 EmptyResultDataAccessException)
        assertThatThrownBy(() -> userService.deleteUser(99999L))
                // 예: .isInstanceOf(EmptyResultDataAccessException.class);
                .isInstanceOf(BaseException.class); // 임시로 넓게
    }
    @Test
    void validateDuplicateUser(){
        userService.createUser(testUser);
        assertThatThrownBy(()->{userService.createUser(testUser);}).isInstanceOf(RuntimeException.class);
    }
    @Test
    void updateUser(){
        Long userId = userService.createUser(testUser);
        User changedUser = User.builder().role(Role.USER)
                .email("Changed")
                .id(userId)
                .loginId("Changed")
                .password("Changed")
                .nickName("Changed")
                .username("Changed").build();
        userService.updateUser(changedUser);
        assertThat(userService.findUserByUserId(userId).getEmail()).isEqualTo("Changed");
    }

}