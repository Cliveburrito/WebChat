    package com.example.WebChat.Service;

    import com.example.WebChat.DTO.*;
    import com.example.WebChat.Entity.User;
    import com.example.WebChat.Exception.EmailAlreadyExistsException;
    import com.example.WebChat.Exception.RateLimitExceededException;
    import com.example.WebChat.Exception.UserAlreadyExistsException;
    import com.example.WebChat.Repository.UserRepository;
    import io.github.bucket4j.Bucket;
    import lombok.RequiredArgsConstructor;
    import lombok.extern.slf4j.Slf4j;
    import org.springframework.security.authentication.AuthenticationManager;
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
        private final RateLimiterService rateLimiter;
        private final PresenceService presenceService;

        public JwtAuthenticationResponse register(RegisterUserRequest request, String ipAddress) {
            Bucket bucket = rateLimiter.resolveAuthBucket(ipAddress);

            if(!bucket.tryConsume(1)) {
                log.warn("Too many register attempts from ip: {}", ipAddress);
                throw new RateLimitExceededException("Too many requests. Please try again in a bit.");
            }
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

            User saved = userRepository.save(user);

            UserResponse userResponse = new UserResponse(
                    saved.getId(),
                    saved.getUsername(),
                    saved.getEmail(),
                    saved.getAvatarUrl()
            );

            var userDetails = org.springframework.security.core.userdetails.User
                    .withUsername(saved.getUsername())
                    .password(saved.getPasswordHash())
                    .authorities("USER")
                    .build();

            String token = jwtService.generateToken(userDetails);

            return new JwtAuthenticationResponse(token, userResponse);
        }


        public JwtAuthenticationResponse login(LoginUserRequest request, String ipAddress) {
            Bucket bucket = rateLimiter.resolveAuthBucket(ipAddress);
            if (!bucket.tryConsume(1)) {
                log.warn("Too many login attempts from ip: {}", ipAddress);
                throw new RateLimitExceededException("Too many requests.");
            }

            // Authenticate - This calls loadUserByUsername and puts CachedUser in the result
            var authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password())
            );
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
            log.info("JWT token issued successfully for user: {}", user.getUsername());

            return new JwtAuthenticationResponse(jwtToken, userResponse);
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


