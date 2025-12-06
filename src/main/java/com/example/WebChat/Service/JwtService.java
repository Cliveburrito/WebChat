package com.example.WebChat.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.util.Date;
import java.util.Map;
import java.util.function.Function;

/**
 * Service responsible for generating, validating, and extracting information from JWTs (JSON Web Tokens).
 * Uses HMAC-SHA256 signing with a symmetric secret key.
 * <p>
 * Key features:
 * - Token generation (with optional extra claims)
 * - Username & claim extraction
 * - Token validation (signature, expiration, subject match)
 * <p>
 * ⚠️ Note: The {@link #SECRET_KEY} must be kept secure in production (e.g., via environment variable).
 */
@Service
public class JwtService {

    /**
     * Secret key for signing and verifying JWTs.
     * Must be at least 256 bits (32 bytes) for HS256.
     * ⚠️ Hardcoded for demo only — NEVER hardcode in production.
     */
    private static final String SECRET_KEY =
            "3f8e6c4b3a2d1f9e7c5b4a39281726354455464758696a6b7c8d9eafb0c1d2e3";

    // ───────────────────────────────────────────────────────
    // CLAIM EXTRACTION METHODS
    // ───────────────────────────────────────────────────────

    /**
     * Extracts the username (subject) from the JWT.
     *
     * @param token the JWT string (must be signed with the correct key)
     * @return the subject (typically the user's username/email)
     * @throws io.jsonwebtoken.JwtException if token is invalid, expired, or malformed
     */
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * Generic method to extract a specific claim from the JWT using a resolver function.
     *
     * @param token    the JWT string
     * @param resolver a function that maps {@link Claims} to the desired type/value
     * @param <T>      the type of the extracted claim
     * @return the resolved claim value
     * @throws io.jsonwebtoken.JwtException if parsing fails
     */
    public <T> T extractClaim(String token, Function<Claims, T> resolver) {
        final Claims claims = extractAllClaims(token);
        return resolver.apply(claims);
    }

    /**
     * Parses and returns all claims from the JWT body.
     * Validates signature using the configured secret key.
     *
     * @param token the JWT string
     * @return the parsed {@link Claims} object
     * @throws io.jsonwebtoken.JwtException if signature is invalid or token is malformed
     */
    private Claims extractAllClaims(String token) {
        return Jwts
                .parserBuilder()
                .setSigningKey(getSignInKey())  // Validates signature during parsing
                .build()
                .parseClaimsJws(token)          // Throws if invalid/expired/signature mismatch
                .getBody();
    }

    /**
     * Extracts the expiration date claim from the token.
     *
     * @param token the JWT
     * @return the {@link Date} when the token expires
     */
    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    // ───────────────────────────────────────────────────────
    // TOKEN GENERATION METHODS
    // ───────────────────────────────────────────────────────

    /**
     * Generates a JWT for the given user with default settings (24-hour expiration, no extra claims).
     *
     * @param userDetails Spring Security user details (e.g., from UserDetailsService)
     * @return the signed JWT string
     */
    public String generateToken(UserDetails userDetails) {
        return generateToken(Map.of(), userDetails);
    }

    /**
     * Generates a JWT with optional extra claims (e.g., roles, permissions).
     *
     * @param extraClaims additional key-value pairs to embed in the token body
     * @param userDetails Spring Security user details
     * @return the signed JWT string
     */
    public String generateToken(Map<String, Object> extraClaims,
                                UserDetails userDetails) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .setClaims(extraClaims)                         // Add custom claims (e.g., "roles": ["USER"])
                .setSubject(userDetails.getUsername())         // Standard "sub" claim
                .setIssuedAt(new Date(now))                    // "iat" — time of issue
                .setExpiration(new Date(now + 1000 * 60 * 60 * 24)) // "exp" — expires in 24 hours
                .signWith(getSignInKey(), SignatureAlgorithm.HS256) // Sign with HMAC-SHA256
                .compact();                                    // Serialize to compact JWT string
    }

    // ───────────────────────────────────────────────────────
    // VALIDATION METHODS
    // ───────────────────────────────────────────────────────

    /**
     * Validates a JWT against a {@link UserDetails} object.
     * Checks:
     * 1. Token is not expired
     * 2. Token's subject matches the username in UserDetails
     * (Signature is validated implicitly during claim extraction)
     *
     * @param token       the JWT string
     * @param userDetails the user to validate against
     * @return {@code true} if valid and matching; {@code false} otherwise
     */
    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername())) && !isTokenExpired(token);
    }

    /**
     * Checks whether the token has expired (i.e., current time > expiration time).
     *
     * @param token the JWT
     * @return {@code true} if expired; {@code false} otherwise
     */
    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    // ───────────────────────────────────────────────────────
    // UTILITY METHODS
    // ───────────────────────────────────────────────────────

    /**
     * Decodes the base64-encoded {@link #SECRET_KEY} and constructs a {@link Key} for HMAC-SHA256 signing.
     * <p>
     * Uses {@link Keys#hmacShaKeyFor(byte[])} to ensure correct key length and security.
     *
     * @return the signing key
     */
    private Key getSignInKey() {
        byte[] keyBytes = Decoders.BASE64.decode(SECRET_KEY);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}