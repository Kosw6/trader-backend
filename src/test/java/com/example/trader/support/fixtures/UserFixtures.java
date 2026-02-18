package com.example.trader.support.fixtures;
import com.example.trader.entity.Team;

import com.example.trader.entity.User;
import com.example.trader.repository.UserRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional
public class UserFixtures {
    private final UserRepository userRepository;

    public UserFixtures(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User save() {
        return userRepository.save(TestFixtures.user());
    }
    //커스텀
    public User save(User user) {
        return userRepository.save(user);
    }
    //제약조건 테스트용
    public User saveAndFlush() {
        return userRepository.saveAndFlush(TestFixtures.user());
    }
}
