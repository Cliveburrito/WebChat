package com.example.WebChat.auth.dto;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import java.util.Collection;


/**
 * Αυτό είναι το "Master" Security DTO.
 * Αντικαθιστά το CachedUser ΚΑΙ το CustomPrincipal.
 */
public record CustomPrincipal(
        Long id,
        String username,
        String password,
        boolean stealthMode,
        boolean enabled,
        Collection<? extends GrantedAuthority> authorities
) implements UserDetails {
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override public String getPassword() { return password; }
    @Override public String getUsername() { return username; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return enabled; }
    public Long getUserId() {return id; }
}