package com.example.pfe.Service;

import com.example.pfe.Repository.UserRepository;
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

@Service
@RequiredArgsConstructor
//UserDetailsService is an interface Spring Security uses to load user data during authentication
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    //find the user and return a UserDetails object that contains username, password, account status, and roles.
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // Try to find by username first, then by email, then by employee code
        User user = userRepository.findByUsername(username)
                .orElseGet(() -> userRepository.findByEmail(username)
                        .orElseGet(() -> userRepository.findByEmployeeCode(username)
                                .orElseThrow(() -> new UsernameNotFoundException(
                                        "User not found with identifier: " + username))));

        if (!user.isEnabled()) {
            throw new UsernameNotFoundException("User account is disabled: " + username);
        }

        // Convert roles to GrantedAuthority
        //Spring Security uses GrantedAuthority objects to represent roles or permissions.
        //This converts your user’s roles (from database) into a list Spring Security can understand.
        List<SimpleGrantedAuthority> authorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority(role.getName().name()))
                .collect(Collectors.toList());
//Spring Security uses this to authenticate and authorize the user.
        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPasswordHash(), // This should be BCrypt encoded
                user.isEnabled(),
                user.isAccountNonExpired(),
                user.isCredentialsNonExpired(),
                user.isAccountNonLocked(),
                authorities
        );
    }
}