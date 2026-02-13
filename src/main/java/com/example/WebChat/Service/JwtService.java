package com.example.WebChat.Service;

import com.example.WebChat.UtilsConfigs.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Service responsible for generating, validating, and extracting information from JWTs
 *
 * <p>Key features:</p>
 * <ul>
 *     <li>Token generation (with optional extra claims)</li>
 *     <li>Username & claim extraction</li>
 *     <li>Token validation (signature, expiration, subject match)</li>
 * </ul>
 */
@Slf4j
@Service
public class JwtService {

    private final String SECRET_KEY;
    private final long EXPIRATION_MS;


    public JwtService(AppProperties appProperties) {
        this.SECRET_KEY = appProperties.getSecurity().getJwtSecret();
        this.EXPIRATION_MS = appProperties.getSecurity().getJwtExpirationMs();
    }

    // ───────────────────────────────────────────────────────
    // CLAIM EXTRACTION METHODS
    // ───────────────────────────────────────────────────────

    /**
     * Extracts the username (subject) from the JWT.
     */
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * Generic method to extract a specific claim from the JWT using a resolver function.
     */
    public <T> T extractClaim(String token, Function<Claims, T> resolver) throws io.jsonwebtoken.JwtException{
        final Claims claims = extractAllClaims(token);
        System.out.println("HERE I PRINT THE CLAIMS: " + claims.toString());
        return resolver.apply(claims);
    }

    /**
     * Parses and returns all claims from the JWT body
     * Signature is validated using the configured secret key
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
     */
    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public Collection<? extends GrantedAuthority> extractAuthorities(String token) {
        Claims claims = extractAllClaims(token);

        Object rolesObj = claims.get("roles");
        if (rolesObj == null) {
            return List.of();
        }

        @SuppressWarnings("unchecked")
        List<String> roles = (List<String>) rolesObj;

        return roles.stream()
                .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                .map(SimpleGrantedAuthority::new)
                .toList();
    }


    // ───────────────────────────────────────────────────────
    // TOKEN GENERATION METHODS
    // ───────────────────────────────────────────────────────

    /**
     * Generates a JWT for the given user with default settings:
     */
    public String generateToken(UserDetails userDetails) {
        log.info("Generating new Token!");
        return generateToken(Map.of(), userDetails);
    }

    /**
     * Generates a JWT with optional extra claims (e.g., roles, permissions).
     */
    public String generateToken(Map<String, Object> extraClaims,
                                UserDetails userDetails) {

        long now = System.currentTimeMillis();

        return Jwts.builder()
                .setClaims(extraClaims)                         // Custom claims (e.g. "roles": ["USER"])
                .setSubject(userDetails.getUsername())         // Standard "sub" claim
                .setIssuedAt(new Date(now))                    // "iat" — time of issue
                .setExpiration(new Date(now + EXPIRATION_MS))  // "exp" — expires based on config
                .signWith(getSignInKey(), SignatureAlgorithm.HS256)  // Sign with HMAC-SHA256
                .compact();                                    // Serialize to compact JWT string
    }

    // ───────────────────────────────────────────────────────
    // VALIDATION METHODS
    // ───────────────────────────────────────────────────────

    /**
     * <p>Checks that:</p>
     * <ol>
     *     <li>The token is not expired.</li>
     *     <li>The token's subject matches the username in {@code userDetails}.</li>
     * </ol>
     *
     * <p>Signature and structural validation are performed during claim extraction.</p>
     */
    public boolean isTokenValid(String token) {
        try {
            extractAllClaims(token); // validates signature + exp
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Checks whether the token has expired
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
     */
    private Key getSignInKey() {
        byte[] keyBytes = Decoders.BASE64.decode(SECRET_KEY);
        return Keys.hmacShaKeyFor(keyBytes);
    }




    // -----------------------------------------------------
    // METHOD FOR TESTING
    // -----------------------------------------------------

    public String generateExpiredToken(String username) {

        long now = System.currentTimeMillis();

        return Jwts.builder()
                .setClaims(null)                         // Custom claims (e.g. "roles": ["USER"])
                .setSubject(username)         // Standard "sub" claim
                .setIssuedAt(new Date(now))                    // "iat" — time of issue
                .setExpiration(new Date(now - 1000L * 60 * 60 * 24)) // "exp" — expires in 24 hours
                .signWith(getSignInKey(), SignatureAlgorithm.HS256)  // Sign with HMAC-SHA256
                .compact();                                    // Serialize to compact JWT string
    }

}
