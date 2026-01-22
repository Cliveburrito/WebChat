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
 *
 * <p>Key features:</p>
 * <ul>
 *     <li>Token generation (with optional extra claims)</li>
 *     <li>Username & claim extraction</li>
 *     <li>Token validation (signature, expiration, subject match)</li>
 * </ul>
 *
 * <p><b>Important:</b> The {@link #SECRET_KEY} must be kept secure in production
 * (e.g. via environment variables, Vault, or Spring Cloud Config), and should
 * be a Base64-encoded value with sufficient length for HS256.</p>
 */
@Service
public class JwtService {

    /**
     * Secret key for signing and verifying JWTs.
     *
     * <p>
     * This value is expected to be <b>Base64-encoded</b>, because it is decoded
     * using {@link Decoders#BASE64}. After decoding, the byte array must have
     * at least 256 bits (32 bytes) of entropy for HS256.
     * </p>
     *
     * <p><b>Warning:</b> Hardcoded here only for demonstration. Do <b>not</b> hardcode
     * secrets in production code.</p>
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
     * @return the subject (typically the user's username or email)
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
    public <T> T extractClaim(String token, Function<Claims, T> resolver) throws io.jsonwebtoken.JwtException{
        final Claims claims = extractAllClaims(token);
        System.out.println("HERE I PRINT THE CLAIMS: " + claims.toString());
        return resolver.apply(claims);
    }

    /**
     * Parses and returns all claims from the JWT body.
     * Signature is validated using the configured secret key.
     *
     * @param token the JWT string
     * @return the parsed {@link Claims} object
     * @throws io.jsonwebtoken.JwtException if signature is invalid or token is malformed/expired
     */
    private Claims extractAllClaims(String token) {
        return Jwts
                .parserBuilder()
                .setSigningKey(getSignInKey())   // Validates signature during parsing
                .build()
                .parseClaimsJws(token)           // Throws if invalid/expired/signature mismatch
                .getBody();
        //This return the registered Claims found in  the payload of a JWT(JSON WEB TOKEN) I used a print on the above method and it printed this
        //{sub=Mitsaras, iat=1765026573, exp=1765112973} so it gives the SUBJECT that has been authenticated and iat / exp is issued time and
        //expiration time
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
     * Generates a JWT for the given user with default settings:
     * <ul>
     *     <li>24-hour expiration</li>
     *     <li>No extra custom claims</li>
     * </ul>
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
                .setClaims(extraClaims)                         // Custom claims (e.g. "roles": ["USER"])
                .setSubject(userDetails.getUsername())         // Standard "sub" claim
                .setIssuedAt(new Date(now))                    // "iat" — time of issue
                .setExpiration(new Date(now + 1000L * 60 * 60 * 24)) // "exp" — expires in 24 hours
                .signWith(getSignInKey(), SignatureAlgorithm.HS256)  // Sign with HMAC-SHA256
                .compact();                                    // Serialize to compact JWT string
    }

    // ───────────────────────────────────────────────────────
    // VALIDATION METHODS
    // ───────────────────────────────────────────────────────

    /**
     * Validates a JWT against a {@link UserDetails} object.
     *
     * <p>Checks that:</p>
     * <ol>
     *     <li>The token is not expired.</li>
     *     <li>The token's subject matches the username in {@code userDetails}.</li>
     * </ol>
     *
     * <p>Signature and structural validation are performed during claim extraction.</p>
     *
     * @param token       the JWT string
     * @param userDetails the user to validate against
     * @return {@code true} if valid and matching; {@code false} otherwise
     */
    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
    }

    /**
     * Checks whether the token has expired (i.e., current time &gt; expiration time).
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
     * Decodes the Base64-encoded {@link #SECRET_KEY} and constructs a {@link Key}
     * suitable for HMAC-SHA256 signing.
     *
     * <p>Uses {@link Keys#hmacShaKeyFor(byte[])} to ensure correct key length and security.</p>
     *
     * @return the signing key
     */
    private Key getSignInKey() {
        byte[] keyBytes = Decoders.BASE64.decode(SECRET_KEY);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
