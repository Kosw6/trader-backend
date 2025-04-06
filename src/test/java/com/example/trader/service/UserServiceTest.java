package com.example.trader.service;

import com.example.trader.entity.User;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.junit.jupiter.api.BeforeEach;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class UserServiceTest {

    @Autowired
    private UserService userService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .email("test@example.com")
                .username("Test User")
                .loginId("test1")
                .password("test1")
                .nick_name("test_user")
                .build();
    }

    @Test
    void findUserByUserId() {
        User userByUserId = userService.findUserByUserId(1L);
        assertThat(1L).isEqualTo(userByUserId.getId());
    }

    @Test
    void findUserByEmail() {
        User userByEmail = userService.findUserByEmail("test@example.com");
        assertThat(1L).isEqualTo(userByEmail.getId());
    }

    @Test
    void createUser() {
        assertThat(1L).isEqualTo(userService.createUser(testUser));
    }


    @Test
    void deleteUser() {
        userService.deleteUser(1L);
        assertThatThrownBy(()->{
            User user = userService.findUserByUserId(1L);
        }).isInstanceOf(Exception.class);
    }
}