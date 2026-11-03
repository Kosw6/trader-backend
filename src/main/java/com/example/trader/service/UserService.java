package com.example.trader.service;

import com.example.trader.entity.Role;
import com.example.trader.entity.User;

import com.example.trader.exception.BaseException;
import com.example.trader.httpresponse.BaseResponseStatus;
import com.example.trader.repository.UserRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.apache.commons.lang3.RandomStringUtils;

@Service
@RequiredArgsConstructor
@Transactional
public class UserService {
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public User findUserByUserId(Long userId) {
        return userRepository.findById(userId).orElseThrow(()->{
            throw new BaseException(BaseResponseStatus.USER_NOT_FOUND);});
    }

    @Transactional(readOnly = true)
    public User findUserByLoginId(String loginId) {
        return userRepository.findByLoginId(loginId).orElseThrow();
    }

    public Long createUser(User user) {
        validateDuplicateUser(user);
        user.changePassword(com.example.trader.security.provider.Password655.encode(user.getPassword()));
        return userRepository.save(user).getId();
    }


    private void validateDuplicateUser(User user) {//중복검사
        if (userRepository.existsByLoginId(user.getLoginId())) {
            throw new BaseException(BaseResponseStatus.EXIST_LOGIN_ID);
        }
        // Verified local signup normalizes email case; also protect legacy mixed-case accounts.
        if (userRepository.existsByEmailIgnoreCase(user.getEmail())) {
            throw new BaseException(BaseResponseStatus.EXIST_EMAIL);
        }
    }
    public Long updateUser(User user){
        User existing = findUserByUserId(user.getId());
        existing.updateProfile655(user);
        existing.changePassword(com.example.trader.security.provider.Password655.encode(user.getPassword()));
        return userRepository.save(existing).getId();
    }

    public void deleteUser(Long userId){
        //todo:컨트롤러에서 컨텍스트에 있는 유저 id로 삭제요청 진행하고 오류 발생하지 않을시에 정상응답 반환 + jwt토큰 만료?? -> 삭제 응답시에 프론트에서 브라우저에 있는 토큰을 지우도록 하자
        if (!userRepository.existsById(userId)) {
            throw new BaseException(BaseResponseStatus.USER_NOT_FOUND);
        }
        userRepository.deleteById(userId);
    }

    public java.util.Optional<User> findOAuthUser(String provider, String providerId) {
        if (provider == null || !java.util.Set.of("google", "kakao", "github").contains(provider) || providerId == null || providerId.isBlank() || "null".equals(providerId))
            throw new org.springframework.security.oauth2.core.OAuth2AuthenticationException("invalid_provider_identity");
        return userRepository.findByProviderAndProviderId(provider, providerId);
    }

    public User upsertOAuthUser(String provider, String providerId, String email, String name) {
        if (provider == null || providerId == null || providerId.isBlank() || "null".equals(providerId)) {
            throw new org.springframework.security.oauth2.core.OAuth2AuthenticationException("invalid_provider_identity");
        }
        var linked = userRepository.findByProviderAndProviderId(provider, providerId);
        if (linked.isPresent()) { linked.get().fillProfileIfEmpty(name); return linked.get(); }
        if (userRepository.findByEmail(email).isPresent() || userRepository.findByLoginId(email).isPresent()) {
            throw new org.springframework.security.oauth2.core.OAuth2AuthenticationException(
                    new org.springframework.security.oauth2.core.OAuth2Error("account_link_required"));
        }
        byte[] secret = new byte[32];
        new java.security.SecureRandom().nextBytes(secret);
        String randomPassword = java.util.Base64.getEncoder().encodeToString(secret);
        User newUser = User.builder().provider(provider).providerId(providerId)
                .email(email).username(name).nickName(name).loginId(email)
                .password(com.example.trader.security.provider.Password655.encode(randomPassword))
                .role(Role.USER).build();
        return userRepository.save(newUser);
    }
}
