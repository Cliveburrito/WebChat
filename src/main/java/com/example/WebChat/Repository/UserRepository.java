package com.example.WebChat.Repository;

import com.example.WebChat.Entity.User;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.lang.NonNull;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User,Long> {
    @Cacheable(value = "users", key = "#username")
    Optional<User> findByUsername(String username);

    User findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    @NonNull
    @Override
    List<User> findAllById(Iterable<Long> ids);

}
