package com.example.WebChat.DTO;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import java.util.Collection;


/**
 * Αυτό είναι το "Master" Security DTO.
 * Αντικαθιστά το CachedUser ΚΑΙ το CustomPrincipal.
 */
public record CustomPrincipal(
        Long id,                // 🚀 Το "ιερό" ID για τα queries
        String username,
        String password,        // Θα είναι null/empty στα JWT requests, αλλά γεμάτο στο Login
        boolean stealthMode,    // Για να ξέρουμε αν θα τον δείξουμε online
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
}