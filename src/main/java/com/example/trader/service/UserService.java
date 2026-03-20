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
//    @Cacheable(cacheNames = "userById",key = "#userId",unless = "#result == null")
    @Transactional(readOnly = true)
    public User findUserByUserId(Long userId) {
        return userRepository.findById(userId).orElseThrow(()->{
            throw new BaseException(BaseResponseStatus.USER_NOT_FOUND);});
    }
//    @Cacheable(cacheNames = "userByLoginId",key = "#loginId",unless = "#result == null")
    @Transactional(readOnly = true)
    public User findUserByLoginId(String loginId) {
        return userRepository.findByLoginId(loginId).orElseThrow();
    }
//    public User findUserByEmail(String email) {
//        return userRepository.findByEmail(email).orElseThrow();
//    }

    public Long createUser(User user) {
        validateDuplicateUser(user);
        return userRepository.save(user).getId();
    }


    private void validateDuplicateUser(User user) {//중복검사
        if (userRepository.findByEmail(user.getEmail()).isPresent()) {
            if(userRepository.findByEmail(user.getLoginId()).isPresent()){
                throw new BaseException(BaseResponseStatus.EXIST_LOGIN_ID);
            }
            throw new BaseException(BaseResponseStatus.EXIST_EMAIL);
        }
    }
    public Long updateUser(User user){
        //todo:컨트롤러에서 SecurityContext에서 유저 id를 찾아서 저장소에서 검색해서 업데이트 시킬 유저를 생성해서 반환
        return userRepository.save(user).getId();
    }

    public void deleteUser(Long userId){
        //todo:컨트롤러에서 컨텍스트에 있는 유저 id로 삭제요청 진행하고 오류 발생하지 않을시에 정상응답 반환 + jwt토큰 만료?? -> 삭제 응답시에 프론트에서 브라우저에 있는 토큰을 지우도록 하자
        if (!userRepository.existsById(userId)) {
            throw new BaseException(BaseResponseStatus.USER_NOT_FOUND);
        }
        userRepository.deleteById(userId);
    }

//    public User findUserByEmailAndProviderId(String email,String providerId){
//        User user = userRepository.findByEmailAndProviderId(email, providerId).orElseThrow(() -> {
//            throw new BaseException(BaseResponseStatus.NON_EXIST_USER);
//        });
//        return user;
//    }

    public User upsertOAuthUser(String provider, String providerId, String email, String name) {

        // 1) 이미 소셜 연동된 계정이면 그대로 사용
        return userRepository.findByProviderAndProviderId(provider, providerId)
                .map(user -> {
                    // email 고정
                    // name도 덮어쓰기 금지, 단 비어있을경우 채우기
                    user.fillProfileIfEmpty(name);
                    return user;
                })
                .orElseGet(() -> {

                    //같은 email로 기존 로컬 계정이 있으면 "연동" 처리
                    return userRepository.findByEmail(email)
                            .map(existing -> {
                                existing.linkOAuth(provider, providerId);
                                existing.fillProfileIfEmpty(name);
                                return existing;
                            })
                            .orElseGet(() -> {

                                // 3) 완전 신규 생성
                                String strongPwd = RandomStringUtils.random(16,
                                        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%");

                                User newUser = User.builder()
                                        .provider(provider)
                                        .providerId(providerId)
                                        .email(email)
                                        .username(name)     // 초기값
                                        .nickName(name)     // 초기값
                                        .loginId(email)     // 정책에 따라
                                        .password(strongPwd)
                                        .role(Role.USER)
                                        .build();

                                return userRepository.save(newUser);
                            });
                });
    }
}