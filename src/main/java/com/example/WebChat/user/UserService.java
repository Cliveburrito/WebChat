package com.example.WebChat.user;

import com.example.WebChat.auth.JwtService;
import com.example.WebChat.auth.RefreshTokenService;
import com.example.WebChat.auth.dto.AuthTokens;
import com.example.WebChat.auth.dto.CustomPrincipal;
import com.example.WebChat.auth.dto.LoginUserRequest;
import com.example.WebChat.auth.dto.RegisterUserRequest;
import com.example.WebChat.presence.PresenceService;
import com.example.WebChat.observability.TrackingLog;
import com.example.WebChat.shared.EmailAlreadyExistsException;
import com.example.WebChat.shared.UserAlreadyExistsException;
import com.example.WebChat.user.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {
        private final UserRepository userRepository;
        private final PasswordEncoder passwordEncoder;
        private final JwtService jwtService;
        private final AuthenticationManager authenticationManager;
        private final PresenceService presenceService;
        private final TrackingLog trackingLog;
        private final RefreshTokenService refreshTokenService;

        @CacheEvict(value = "global_users", allEntries = true)
        public AuthTokens register(RegisterUserRequest request) {
            trackingLog.checkpoint("service.enter", "service", "user.register");
            if (userRepository.existsByUsername(request.username())) {
                throw new UserAlreadyExistsException("Username already in use");
            }
            if (userRepository.existsByEmail(request.email())) {
                throw new EmailAlreadyExistsException("Email already in use");
            }

            User user = new User();
            user.setUsername(request.username());
            user.setEmail(request.email());
            user.setPasswordHash(passwordEncoder.encode(request.password()));
            user.setCreatedAt(Instant.now());
            user.setEnabled(true);
            user.setStealthMode(false);

            User saved = userRepository.save(user);

            UserResponse userResponse = new UserResponse(
                    saved.getId(),
                    saved.getUsername(),
                    saved.getEmail(),
                    saved.getAvatarUrl()
            );

            CustomPrincipal principal = new CustomPrincipal(
                    saved.getId(),
                    saved.getUsername(),
                    saved.getPasswordHash(),
                    saved.isStealthMode(),
                    saved.isEnabled(),
                    java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER"))
            );

            String token = jwtService.generateToken(principal);
            String refreshToken = refreshTokenService.issue(saved);

            return new AuthTokens(token, refreshToken, userResponse);
        }


        public AuthTokens login(LoginUserRequest request) {
            trackingLog.checkpoint("service.enter", "service", "user.login");
            trackingLog.checkpoint("auth.enter");
            // Authenticate - This calls loadUserByUsername and puts CachedUser in the result
            var authentication = authenticate(request);
            log.info("Authentication successful");

            // Get the UserDetails directly from the authentication result
            var userDetails = (org.springframework.security.core.userdetails.UserDetails) authentication.getPrincipal();

            // Fetch the entity for the UserResponse (Non-security data)
            User user = userRepository.findByUsername(request.username())
                    .orElseThrow(() -> new IllegalArgumentException("Invalid username"));

            UserResponse userResponse = new UserResponse(
                    user.getId(),
                    user.getUsername(),
                    user.getEmail(),
                    user.getAvatarUrl()
            );

            // Generate token using the userDetails we got from the Manager
            String jwtToken = jwtService.generateToken(userDetails);
            String refreshToken = refreshTokenService.issue(user);
            log.info("JWT token issued successfully for userId: {}", user.getId());

            return new AuthTokens(jwtToken, refreshToken, userResponse);
        }

        private org.springframework.security.core.Authentication authenticate(LoginUserRequest request) {
            try {
                var authentication = authenticationManager.authenticate(
                        new UsernamePasswordAuthenticationToken(request.username(), request.password())
                );
                trackingLog.checkpoint("auth.success");
                return authentication;
            } catch (AuthenticationException ex) {
                trackingLog.checkpoint("auth.fail", "reason", ex.getClass().getSimpleName());
                throw ex;
            }
        }

        @Transactional
        public void toggleStealthMode(Long id, String username, boolean enabled) {
            // Update the Database
            userRepository.updateStealthMode(id, enabled);

            // Update the Global Presence with Redis
            if (enabled) {
                // When invisible just handle it like they disconnected
                presenceService.onDisconnect(username);
                log.info("User {} went into Stealth Mode (Hidden)", username);
            } else {
                // When visible we put them back in the global list like they have just connected:)
                presenceService.onConnect(username);
                log.info("User {} is now Visible", username);
            }
        }
    }


