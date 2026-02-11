package com.example.WebChat.Repository;

import com.example.WebChat.Entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.NonNull;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User,Long> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    @NonNull
    @Override
    List<User> findAllById(Iterable<Long> ids);

    @Query("SELECT u.stealthMode FROM User u WHERE u.username = :username")
    boolean isStealthModeEnabled(@Param("username") String username);

    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.stealthMode = :enabled WHERE u.username = :username")
    void updateStealthMode(@Param("username") String username, @Param("enabled") boolean enabled);

}
