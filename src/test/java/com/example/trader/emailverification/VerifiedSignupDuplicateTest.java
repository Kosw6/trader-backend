package com.example.trader.emailverification;

import com.example.trader.entity.User;
import com.example.trader.exception.BaseException;
import com.example.trader.httpresponse.BaseResponseStatus;
import com.example.trader.repository.UserRepository;
import com.example.trader.service.UserService;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VerifiedSignupDuplicateTest {
    @Test void legacyEmailCaseCannotCreateAnotherAccount() {
        var repository=mock(UserRepository.class);
        when(repository.existsByEmailIgnoreCase("person@example.com")).thenReturn(true);
        var error=assertThrows(BaseException.class,()->new UserService(repository).createUser(
                User.builder().email("person@example.com").loginId("new-id").password("fixture")
                        .username("fixture").nickName("fixture").role(com.example.trader.entity.Role.USER).build()));
        assertEquals(BaseResponseStatus.EXIST_EMAIL,error.getStatus());
        verify(repository,never()).save(any());
    }
    @Test void duplicateLoginIdCheckedAsLoginIdInsteadOfEmail() {
        var repository=mock(UserRepository.class);when(repository.existsByLoginId("used-id")).thenReturn(true);
        var error=assertThrows(BaseException.class,()->new UserService(repository).createUser(
                User.builder().email("person@example.com").loginId("used-id").password("fixture")
                        .username("fixture").nickName("fixture").role(com.example.trader.entity.Role.USER).build()));
        assertEquals(BaseResponseStatus.EXIST_LOGIN_ID,error.getStatus());
        verify(repository,never()).save(any());
    }
}
