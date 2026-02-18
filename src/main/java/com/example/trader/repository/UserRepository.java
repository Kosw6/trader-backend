package com.example.trader.repository;

import com.example.trader.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User,Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByUsername(String username);
    Optional<User> findByLoginId(String userId);
    Optional<User> findByEmailAndProviderId(String email,String providerId);
    Optional<User> findByProviderAndProviderId(String provider, String providerId);
}
