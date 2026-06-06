package com.example.trader.service;

import com.example.trader.entity.Role;
import com.example.trader.entity.User;
import com.example.trader.exception.BaseException;
import com.example.trader.httpresponse.BaseResponseStatus;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

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
    void findUserByUserId_존재하는유저_반환() {
        Long userId = userService.createUser(testUser);

        User found = userService.findUserByUserId(userId);

        assertThat(found.getId()).isEqualTo(userId);
        assertThat(found.getLoginId()).isEqualTo(testUser.getLoginId());
    }

    @Test
    void findUserByUserId_없는ID_예외() {
        assertThatThrownBy(() -> userService.findUserByUserId(99999L))
                .isInstanceOf(BaseException.class)
                .satisfies(e -> assertThat(((BaseException) e).getStatus())
                        .isEqualTo(BaseResponseStatus.USER_NOT_FOUND));
    }

    @Test
    void createUser_정상저장() {
        Long userId = userService.createUser(testUser);

        assertThat(userId).isNotNull();
        assertThat(userService.findUserByUserId(userId).getLoginId())
                .isEqualTo(testUser.getLoginId());
    }

    @Test
    void createUser_이메일_중복시_예외() {
        userService.createUser(testUser);

        assertThatThrownBy(() -> userService.createUser(testUser))
                .isInstanceOf(BaseException.class)
                .satisfies(e -> assertThat(((BaseException) e).getStatus())
                        .isEqualTo(BaseResponseStatus.EXIST_EMAIL));
    }

    @Test
    void deleteUser_정상삭제_이후조회시_예외() {
        Long userId = userService.createUser(testUser);

        userService.deleteUser(userId);

        assertThatThrownBy(() -> userService.findUserByUserId(userId))
                .isInstanceOf(BaseException.class)
                .satisfies(e -> assertThat(((BaseException) e).getStatus())
                        .isEqualTo(BaseResponseStatus.USER_NOT_FOUND));
    }

    @Test
    void deleteUser_없는ID_예외() {
        assertThatThrownBy(() -> userService.deleteUser(99999L))
                .isInstanceOf(BaseException.class)
                .satisfies(e -> assertThat(((BaseException) e).getStatus())
                        .isEqualTo(BaseResponseStatus.USER_NOT_FOUND));
    }

    @Test
    void updateUser_정상변경() {
        Long userId = userService.createUser(testUser);

        User changedUser = User.builder()
                .id(userId)
                .email("changed@test.com")
                .username("Changed")
                .loginId("changedID")
                .password("changedPW")
                .nickName("changedNick")
                .role(Role.USER)
                .build();

        userService.updateUser(changedUser);

        assertThat(userService.findUserByUserId(userId).getEmail())
                .isEqualTo("changed@test.com");
    }
}
