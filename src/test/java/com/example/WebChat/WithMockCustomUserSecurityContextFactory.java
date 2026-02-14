package com.example.WebChat;

import com.example.WebChat.DTO.CustomPrincipal;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithSecurityContextFactory;

import java.util.List;

public class WithMockCustomUserSecurityContextFactory implements WithSecurityContextFactory<WithMockCustomUser> {
    @Override
    public SecurityContext createSecurityContext(WithMockCustomUser annotation) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();

        // Φτιάχνουμε το CustomPrincipal που περιμένει ο Controller
        CustomPrincipal principal = new CustomPrincipal(
                annotation.id(),
                annotation.username(),
                null, false, true,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );

        Authentication auth = new UsernamePasswordAuthenticationToken(
                principal, "password", principal.getAuthorities()
        );
        context.setAuthentication(auth);
        return context;
    }
}