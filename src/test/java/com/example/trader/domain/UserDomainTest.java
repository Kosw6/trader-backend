package com.example.trader.domain;

import com.example.trader.dto.RequestUserDto;
import com.example.trader.entity.Note;
import com.example.trader.entity.Role;
import com.example.trader.entity.User;
import com.example.trader.exception.BaseException;
import com.example.trader.httpresponse.BaseResponseStatus;
import com.example.trader.support.fixtures.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

public class UserDomainTest {

    @Test
    @DisplayName("User.of는 RequestUserDto를 User 엔티티로 매핑하고 role은 기본 USER로 설정한다")
    void of_maps_fields_and_sets_role_user() {
        RequestUserDto dto = new RequestUserDto(
                123L,
                "a@a.com",
                "홍길동",
                20,
                "login",
                "pw",
                "MALE", // dto 타입에 맞게 수정
                "nick"
        );

        User user = User.of(dto);

        assertThat(user.getId()).isEqualTo(123L);
        assertThat(user.getEmail()).isEqualTo("a@a.com");
        assertThat(user.getUsername()).isEqualTo("홍길동");
        assertThat(user.getAge()).isEqualTo(20);
        assertThat(user.getLoginId()).isEqualTo("login");
        assertThat(user.getPassword()).isEqualTo("pw");
        assertThat(user.getNickName()).isEqualTo("nick");
        assertThat(user.getRole()).isEqualTo(Role.USER);
    }

    @Test
    @DisplayName("loginId 정상 변경")
    void changeLoginId_success() {

        User user = TestFixtures.user();

        user.changeLoginId("newLoginId");

        assertThat(user.getLoginId()).isEqualTo("newLoginId");
    }

    @Test
    @DisplayName("loginId null이면 INVALID_USER 예외")
    void changeLoginId_null_throws() {

        User user = TestFixtures.user();

        Throwable t = catchThrowable(() -> user.changeLoginId(null));

        assertThat(t).isInstanceOf(BaseException.class);
        assertThat(((BaseException)t).getStatus())
                .isEqualTo(BaseResponseStatus.INVALID_USER);
    }

    @Test
    @DisplayName("loginId blank면 INVALID_USER 예외")
    void changeLoginId_blank_throws() {

        User user = TestFixtures.user();

        Throwable t = catchThrowable(() -> user.changeLoginId("  "));

        assertThat(t).isInstanceOf(BaseException.class);
    }


    @Test
    @DisplayName("changeNickName은 nickName을 변경한다")
    void changeNickName_updates_nickName() {
        User user = TestFixtures.user();

        user.changeNickName("newNick");

        assertThat(user.getNickName()).isEqualTo("newNick");
    }

    @Test
    @DisplayName("changeNickName은 null이면 INVALID_USER 예외가 발생한다")
    void changeNickName_null_throws() {
        User user = TestFixtures.user();

        assertThatThrownBy(() -> user.changeNickName(null))
                .isInstanceOfSatisfying(BaseException.class, ex ->
                        assertThat(ex.getStatus()).isEqualTo(BaseResponseStatus.INVALID_USER)
                )
                .hasMessageContaining("닉네임");
    }

    @Test
    @DisplayName("changeNickName은 blank이면 INVALID_USER 예외가 발생한다")
    void changeNickName_blank_throws() {
        User user = TestFixtures.user();

        assertThatThrownBy(() -> user.changeNickName("  "))
                .isInstanceOfSatisfying(BaseException.class, ex ->
                        assertThat(ex.getStatus()).isEqualTo(BaseResponseStatus.INVALID_USER)
                ).hasMessageContaining("닉네임");
    }

    @Test
    @DisplayName("changePassword는 password를 변경한다")
    void changePassword_updates_password() {
        User user = TestFixtures.user();

        user.changePassword("encodedNewPw");

        assertThat(user.getPassword()).isEqualTo("encodedNewPw");
    }

    @Test
    @DisplayName("changePassword는 null이면 INVALID_USER 예외가 발생한다")
    void changePassword_null_throws() {
        User user = TestFixtures.user();

        assertThatThrownBy(() -> user.changePassword(null))
                .isInstanceOfSatisfying(BaseException.class, ex ->
                        assertThat(ex.getStatus()).isEqualTo(BaseResponseStatus.INVALID_USER)
                )
                .hasMessageContaining("비밀번호");
    }

    @Test
    @DisplayName("changePassword는 blank이면 INVALID_USER 예외가 발생한다")
    void changePassword_blank_throws() {
        User user = TestFixtures.user();

        assertThatThrownBy(() -> user.changePassword("   "))
                .isInstanceOfSatisfying(BaseException.class, ex ->
                        assertThat(ex.getStatus()).isEqualTo(BaseResponseStatus.INVALID_USER)
                ).hasMessageContaining("비밀번호");
    }
    
    @Test
    @DisplayName("linkOAuth는 provider/providerId를 설정한다")
    void linkOAuth_sets_provider_and_providerId() {
        User user = TestFixtures.user();

        user.linkOAuth("google", "pid-123");

        assertThat(user.getProvider()).isEqualTo("google");
        assertThat(user.getProviderId()).isEqualTo("pid-123");
    }


    @Test
    @DisplayName("fillProfileIfEmpty는 기존 username/nickName이 있으면 덮어쓰지 않는다")
    void fillProfileIfEmpty_does_not_override_existing() {
        User user = TestFixtures.user();
        String oldUserName = user.getUsername();
        String oldNick = user.getNickName();
        user.fillProfileIfEmpty("NewName");

        assertThat(user.getUsername()).isEqualTo(oldUserName);
        assertThat(user.getNickName()).isEqualTo(oldNick);
    }




}
