package com.example.WebChat.UtilsConfigs;

import com.example.WebChat.Service.CustomUserDetailsService;
import com.example.WebChat.Service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 🔐 JWT Authentication Filter — runs on *every* incoming HTTP request (except /login, /register, etc. if excluded).
 *
 * 🎯 Goal: If a valid JWT is present in the "Authorization: Bearer <token>" header,
 *         authenticate the user and attach their identity to the request.
 *
 * ⚙️ Where it fits:
 *   Browser → [Spring Filter Chain] → JwtAuthenticationFilter → [Other Filters] → Controller
 *
 * 📝 Note: Extends {@link OncePerRequestFilter} (not raw Servlet Filter) to avoid double-invocation in async/error scenarios.
 */
@Component
@RequiredArgsConstructor // ← Injects jwtService & userDetailsService via constructor (clean, testable)
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;

    /**
     * 🧵 Core method: Processes each HTTP request *before* it reaches your controller.
     *
     * ⚠️ Critical: Must call {@code filterChain.doFilter(request, response)} at the end —
     *              otherwise the request STOPS here (504/timeout!).
     *
     * @param request  the incoming HTTP request
     * @param response the HTTP response (can be modified, e.g., 401)
     * @param filterChain the rest of the Spring Security filter chain
     */
    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        // 🔍 Step 1: Extract the "Authorization" header (e.g., "Bearer abc.xyz.123")
        System.out.println("✅✅✅✅✅FILTER USED");
        final String authHeader = request.getHeader("Authorization");
        String jwt = null;
        String username = null;

        // 🚫 If no header, or not "Bearer ..." → skip JWT auth (e.g., public endpoints)
        //    Let the request proceed — maybe it’s /login, or a public resource.
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response); // ✅ Pass control to next filter
            return;
        }

        // ✂️ Step 2: Extract raw JWT (remove "Bearer " prefix → 7 chars)
        jwt = authHeader.substring(7);  // "Bearer abc" → "abc"

        // 🔐 Step 3: Try to extract username from JWT (validates signature & structure!)
        //    ⚠️ This may throw JwtException (e.g., invalid signature, malformed) → currently unhandled!
        //    (Later: add try-catch + 401 response — see notes below)
        username = jwtService.extractUsername(jwt);

        // ✅ Step 4: Only proceed if:
        //    - We got a username from the token, AND
        //    - No authentication already exists (avoid overriding existing auth, e.g., from session)
        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {

            // 🗃️ Step 5: Load full user details from DB (needed for roles, password hash check)
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);

            // 🔑 Step 6: Validate the token against the user (checks: signature, expiration, username match)
            if (jwtService.isTokenValid(jwt, userDetails)) {

                // 🪪 Step 7: Create an *authenticated* token (Spring Security’s way of saying "user is logged in")
                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(
                                userDetails,     // Principal (the user)
                                null,            // Credentials → null (we don’t store password in context!)
                                userDetails.getAuthorities() // Roles/permissions
                        );

                // 📍 Step 8: Attach request metadata (IP, session, etc.) — useful for audit/security logs
                authToken.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request)
                );

                // 🧠 Step 9: ✅ THE MOST IMPORTANT LINE:
                //    Store the authentication in Spring’s *thread-local* security context.
                //    → Now, ANYWHERE in this request (controllers, services), you can call:
                //         SecurityContextHolder.getContext().getAuthentication().getName()
                //    → Also enables @PreAuthorize, hasRole(), etc.
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
            // ❗ If token is invalid → do *nothing*. Request proceeds unauthenticated (will be blocked later by @PreAuthorize or 403).
        }

        // 🔄 Step 10: ALWAYS pass the request down the chain — even if auth failed!
        filterChain.doFilter(request, response);
    }
}