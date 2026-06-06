package com.example.pfe.Service;

import com.example.pfe.Repository.UserRepository;
import com.example.pfe.config.UserPrincipal;
import com.example.pfe.entities.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;
//This class is Spring Security's bridge to your database.
// It tells Spring Security how to load user information when someone tries to login.
//Think of it as a translator between:
// database (with  User entity)
//Spring Security (which needs its own UserDetails object
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    //This method is automatically called by Spring Security when someone tries to login
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "User not found with email: " + email));

        if (!user.isEnabled()) {
            throw new UsernameNotFoundException("User account is disabled: " + email);
        }

        // ← Only this line changes: return UserPrincipal instead of Spring's User
        return UserPrincipal.from(user);
    }}
//Without this class, Spring Security would have no idea:
//
//How to find users in YOUR database
//
//What YOUR user fields mean (enabled, roles, etc.)
//
//Which field is the username (email in your case)




// When user logs in:
// 1. LoginController calls authenticationService.authenticate()
// 2. authenticate() calls validateCredentials() which checks manually

// BUT Spring Security also needs to know about users for:
// - Protecting URLs (@PreAuthorize)
// - Checking roles in controllers
// - Session management

// This CustomUserDetailsService provides that info to Spring Security