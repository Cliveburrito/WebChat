package com.example.WebChat.user;

import com.example.WebChat.user.User;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.NonNull;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User,Long> {
    @Cacheable(value = "user_entities", key = "#username")
    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    @NonNull
    @Override
    List<User> findAllById( Iterable<Long> ids);

    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.stealthMode = :enabled WHERE u.id = :userId")
    void updateStealthMode(@Param("userId") Long userId, @Param("enabled") boolean enabled);

}
