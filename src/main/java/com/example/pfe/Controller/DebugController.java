package com.example.pfe.Controller;

import com.example.pfe.Service.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/debug")
@RequiredArgsConstructor
public class DebugController {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @GetMapping("/token-info/{token}")
    public ResponseEntity<?> getTokenInfo(@PathVariable String token) {
        try {
            Map<String, Object> info = new HashMap<>();

            // Extraire les claims
            String subject = jwtService.extractUsername(token);
            String email = jwtService.extractUserEmail(token);
            String type = jwtService.getTokenType(token);

            info.put("tokenSubject", subject);
            info.put("tokenEmail", email);
            info.put("tokenType", type);
            info.put("isValid", jwtService.isTokenValid(token));
            info.put("isAccessToken", jwtService.isAccessToken(token));

            // Charger UserDetails
            if (subject != null) {
                try {
                    UserDetails userDetails = userDetailsService.loadUserByUsername(subject);
                    info.put("userDetailsUsername", userDetails.getUsername());
                    info.put("userDetailsAuthorities", userDetails.getAuthorities());
                    info.put("tokenValidForUser", jwtService.isTokenValid(token, userDetails));
                } catch (Exception e) {
                    info.put("userDetailsError", e.getMessage());

                    // Essayer avec l'email
                    if (email != null && !email.equals(subject)) {
                        try {
                            UserDetails userDetailsByEmail = userDetailsService.loadUserByUsername(email);
                            info.put("userDetailsByEmailUsername", userDetailsByEmail.getUsername());
                            info.put("tokenValidForEmailUser", jwtService.isTokenValid(token, userDetailsByEmail));
                        } catch (Exception e2) {
                            info.put("emailUserDetailsError", e2.getMessage());
                        }
                    }
                }
            }

            return ResponseEntity.ok(info);

        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }
}
