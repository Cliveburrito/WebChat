package com.example.WebChat.observability;

import com.example.WebChat.auth.dto.CustomPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SafePrincipal {
    private SafePrincipal() {
    }

    public static String currentSafeId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return "anonymous";
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof CustomPrincipal customPrincipal) {
            return "userId:" + customPrincipal.id();
        }

        if (principal == null || "anonymousUser".equals(principal)) {
            return "anonymous";
        }

        return "authenticated";
    }
}
