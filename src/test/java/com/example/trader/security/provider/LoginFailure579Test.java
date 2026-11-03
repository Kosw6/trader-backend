package com.example.trader.security.provider;

import com.example.trader.common.GlobalExceptionHandler;
import com.example.trader.controller.loginController;
import com.example.trader.entity.Role;
import com.example.trader.entity.User;
import com.example.trader.security.service.FormUserDetailService;
import com.example.trader.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.NoSuchElementException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class LoginFailure579Test {
    UserService users;
    FormUserDetailService details;
    JwtTokenProvider tokens;
    MockMvc mvc;

    @BeforeEach void setup() {
        users=mock(UserService.class);details=new FormUserDetailService(users);
        tokens=mock(JwtTokenProvider.class);
        var manager=new ProviderManager(new FormAuthenticationProvider(details));
        mvc=MockMvcBuilders.standaloneSetup(new loginController(tokens,manager,users,mock(com.example.trader.policy.PolicyConsentService.class), mock(com.example.trader.emailverification.EmailVerificationService.class)))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    private String rejected() throws Exception {
        var result=mvc.perform(post("/api/login/signin").contentType("application/json")
                .content("{\"loginId\":\"synthetic579\",\"password\":\"incorrect\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("FAIL_AUTHENTICATE"))
                .andExpect(jsonPath("$.message").value("인증에 실패했습니다."))
                .andExpect(header().doesNotExist("Set-Cookie")).andReturn();
        verifyNoInteractions(tokens);
        String body=result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(body).doesNotContain("synthetic579","incorrect","NoSuchElement","Invalid credentials");
        return body;
    }

    @Test void unknown_or_deleted_account_and_wrong_password_share_public_401() throws Exception {
        when(users.findUserByLoginId("synthetic579")).thenThrow(new NoSuchElementException());
        String absent=rejected();
        doReturn(User.builder().id(1L)
                .email("synthetic@example.invalid").username("QA").loginId("synthetic579")
                .password("different").nickName("QA").role(Role.USER).build())
                .when(users).findUserByLoginId("synthetic579");
        assertThat(rejected()).isEqualTo(absent);
    }

    @Test void defensive_null_lookup_is_also_401_without_an_account_hint() throws Exception {
        when(users.findUserByLoginId("synthetic579")).thenReturn(null);rejected();
    }

    @Test void database_failure_is_not_misreported_as_bad_credentials() {
        var unavailable=new IllegalStateException("synthetic database unavailable");
        when(users.findUserByLoginId("synthetic579")).thenThrow(unavailable);
        assertThatThrownBy(()->details.loadUserByUsername("synthetic579")).isSameAs(unavailable)
                .isNotInstanceOf(UsernameNotFoundException.class);
    }
}
