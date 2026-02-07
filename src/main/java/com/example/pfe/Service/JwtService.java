// java
package com.example.pfe.Service;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.WeakKeyException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
public class JwtService {

    @Value("${jwt.secret:defaultsecretchangeme}")
    private String secret;

    @Value("${jwt.clock-skew-seconds:60}")
    private long clockSkewSeconds;

    @Value("${jwt.access-token-expiration-ms:3600000}") // 1h
    private long accessTokenExpirationMs;

    @Value("${jwt.activation-token-expiration-ms:604800000}") // 7 days
    private long activationTokenExpirationMs;

    private Key signingKey;

    @PostConstruct
    public void init() {
        try {
            if (secret == null || secret.trim().isEmpty()) {
                log.warn("jwt.secret is not set - generating a secure random key for JWTs");
                signingKey = Keys.secretKeyFor(SignatureAlgorithm.HS256);
                return;
            }

            byte[] keyBytes;
            // try to decode as Base64 first, fallback to UTF-8 bytes
            try {
                keyBytes = Decoders.BASE64.decode(secret);
            } catch (IllegalArgumentException e) {
                keyBytes = secret.getBytes(StandardCharsets.UTF_8);
            }

            int bits = keyBytes.length * 8;
            if (bits < 256) {
                log.warn("Provided jwt.secret is too weak ({} bits). Generating a secure random key.", bits);
                signingKey = Keys.secretKeyFor(SignatureAlgorithm.HS256);
            } else {
                signingKey = Keys.hmacShaKeyFor(keyBytes);
            }
        } catch (WeakKeyException e) {
            log.warn("WeakKeyException while creating signing key - generating secure random key", e);
            signingKey = Keys.secretKeyFor(SignatureAlgorithm.HS256);
        } catch (Exception e) {
            log.error("Unexpected error while initializing JwtService signing key: {}", e.getMessage(), e);
            signingKey = Keys.secretKeyFor(SignatureAlgorithm.HS256);
        }
    }

    public Optional<String> extractUsernameOptional(String token) {
        try {
            Claims claims = parseClaims(token);
            return Optional.ofNullable(claims.getSubject());
        } catch (JwtException e) {
            log.debug("extractUsernameOptional: cannot parse token: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public String extractUsername(String token) {
        return extractUsernameOptional(token).orElse(null);
    }

    // Nouvelle méthode générique pour extraire n'importe quel claim
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        try {
            Claims claims = parseClaims(token);
            return claimsResolver.apply(claims);
        } catch (JwtException e) {
            log.debug("extractClaim: cannot parse token: {}", e.getMessage());
            return null;
        }
    }

    public String generateTokenWithExpiration(Map<String, Object> claims, String subject, long expirationMillis) {
        long now = System.currentTimeMillis();
        Date issuedAt = new Date(now);
        Date exp = new Date(now + expirationMillis);

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(issuedAt)
                .setExpiration(exp)
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public String generateActivationToken(com.example.pfe.entities.User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("email", user.getEmail());
        claims.put("type", "ACCOUNT_ACTIVATION");
        return generateTokenWithExpiration(claims, activationTokenExpirationMs);
    }

    public boolean isAccessToken(String token) {
        try {
            Claims claims = parseClaims(token);
            Object type = claims.get("type");
            // Accepter "access" ou "ACCESS" (insensible à la casse)
            return type != null && "access".equalsIgnoreCase(type.toString());
        } catch (JwtException e) {
            log.debug("isAccessToken: token parse failed: {}", e.getMessage());
            return false;
        }
    }

    public boolean isActivationToken(String token) {
        try {
            Claims claims = parseClaims(token);
            Object type = claims.get("type");
            boolean ok = "ACCOUNT_ACTIVATION".equals(type);
            if (!ok) log.debug("isActivationToken: claim 'type' is {} (expected 'ACCOUNT_ACTIVATION')", type);
            return ok;
        } catch (JwtException e) {
            log.debug("isActivationToken: token parse failed: {}", e.getMessage());
            return false;
        }
    }

    public boolean isTokenValid(String token) {
        try {
            Claims claims = parseClaims(token);
            Date exp = claims.getExpiration();
            if (exp != null && exp.before(new Date(System.currentTimeMillis() - clockSkewSeconds * 1000))) {
                log.debug("isTokenValid: token expired at {}", exp);
                return false;
            }
            return true;
        } catch (ExpiredJwtException e) {
            log.debug("isTokenValid: expired: {}", e.getMessage());
            return false;
        } catch (JwtException e) {
            log.debug("isTokenValid: invalid token: {}", e.getMessage());
            return false;
        }
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        try {
            Claims claims = parseClaims(token);

            String subject = claims.getSubject();
            if (subject == null) {
                log.debug("isTokenValid(user): token has no subject");
                return false;
            }

            if (!subject.equals(userDetails.getUsername())) {
                log.debug("isTokenValid(user): token subject '{}' does not match user '{}'", subject, userDetails.getUsername());
                return false;
            }

            Date exp = claims.getExpiration();
            if (exp != null && exp.before(new Date(System.currentTimeMillis() - clockSkewSeconds * 1000))) {
                log.debug("isTokenValid(user): token expired at {}", exp);
                return false;
            }

            Object type = claims.get("type");
            // Accepter "access" (minuscules) ET "ACCESS" (majuscules)
            if (type != null) {
                String typeStr = type.toString();
                boolean isValidType = "access".equalsIgnoreCase(typeStr)
                        || "ACCOUNT_ACTIVATION".equals(typeStr);

                if (!isValidType) {
                    log.debug("isTokenValid(user): unexpected token type '{}'", typeStr);
                    return false;
                }
            }

            return true;
        } catch (ExpiredJwtException e) {
            log.debug("isTokenValid(user): expired: {}", e.getMessage());
            return false;
        } catch (JwtException e) {
            log.debug("isTokenValid(user): invalid/signature failed: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            log.error("isTokenValid(user): unexpected error: {}", e.getMessage());
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parserBuilder()
                .setAllowedClockSkewSeconds(clockSkewSeconds)
                .setSigningKey(signingKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    // --- Helper methods used by ActivationService ---

    public Long extractUserId(String token) {
        try {
            Claims claims = parseClaims(token);
            Object idClaim = claims.get("userId");
            if (idClaim == null) {
                String sub = claims.getSubject();
                if (sub != null) {
                    try {
                        return Long.valueOf(sub);
                    } catch (NumberFormatException ignored) {}
                }
                return null;
            }
            if (idClaim instanceof Number) {
                return ((Number) idClaim).longValue();
            } else {
                try {
                    return Long.valueOf(idClaim.toString());
                } catch (NumberFormatException e) {
                    log.debug("extractUserId: cannot convert claim to Long: {}", idClaim);
                    return null;
                }
            }
        } catch (JwtException e) {
            log.debug("extractUserId: invalid token: {}", e.getMessage());
            return null;
        }
    }

    public String getTokenType(String token) {
        try {
            Claims claims = parseClaims(token);
            Object type = claims.get("type");
            return type != null ? type.toString() : null;
        } catch (JwtException e) {
            log.debug("getTokenType: invalid token: {}", e.getMessage());
            return null;
        }
    }

    public String extractUserEmail(String token) {
        return extractUsername(token);
    }

    public long getRemainingTime(String token) {
        try {
            Claims claims = parseClaims(token);
            Date exp = claims.getExpiration();
            if (exp == null) return 0L;
            return Math.max(0L, exp.getTime() - System.currentTimeMillis());
        } catch (JwtException e) {
            log.debug("getRemainingTime: invalid token: {}", e.getMessage());
            return 0L;
        }
    }

    public String generateTokenWithExpiration(Map<String, Object> claims, long expirationMillis) {
        long now = System.currentTimeMillis();
        Date issuedAt = new Date(now);
        Date exp = new Date(now + expirationMillis);

        return Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(issuedAt)
                .setExpiration(exp)
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public String generateAccessToken(com.example.pfe.entities.User user) {
        long now = System.currentTimeMillis();
        Date issuedAt = new Date(now);
        Date exp = new Date(now + accessTokenExpirationMs);

        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("type", "access");  // ← CHANGEZ EN MINUSCULES POUR ÊTRE COHÉRENT
        claims.put("email", user.getEmail());
        claims.put("employeeCode", user.getEmployeeCode());

        if (user.getRoles() != null) {
            List<String> roles = user.getRoles().stream()
                    .map(role -> "ROLE_" + role.getName().name())
                    .collect(Collectors.toList());
            claims.put("roles", roles);
        }

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(user.getEmployeeCode())
                .setIssuedAt(issuedAt)
                .setExpiration(exp)
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }
}