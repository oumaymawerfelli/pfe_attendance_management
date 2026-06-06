package com.example.pfe.config;


import com.example.pfe.entities.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Wraps the User entity as a Spring Security principal.
 *
 * Carrying userId here means controllers NEVER need a DB call
 * just to resolve "who is this request from?" — including SSE endpoints.
 */
@Getter
public class UserPrincipal implements UserDetails {

    private final Long   id;
    private final String email;
    private final String password;
    private final boolean enabled;
    private final boolean accountNonExpired;
    private final boolean accountNonLocked;
    private final boolean credentialsNonExpired;
    private final Collection<? extends GrantedAuthority> authorities;

    private UserPrincipal(User user) {
        this.id                    = user.getId();
        this.email                 = user.getEmail();
        this.password              = user.getPasswordHash();
        this.enabled               = user.isEnabled();
        this.accountNonExpired     = user.isAccountNonExpired();
        this.accountNonLocked      = user.isAccountNonLocked();
        this.credentialsNonExpired = user.isCredentialsNonExpired();
        this.authorities           = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.getName().name()))
                .collect(Collectors.toList());
    }

    /** Factory method — call this in CustomUserDetailsService */
    public static UserPrincipal from(User user) {
        return new UserPrincipal(user);
    }

    // ── UserDetails contract ──────────────────────────────────
    @Override public String getUsername()                                          { return email; }
    @Override public String getPassword()                                          { return password; }
    @Override public boolean isEnabled()                                           { return enabled; }
    @Override public boolean isAccountNonExpired()                                 { return accountNonExpired; }
    @Override public boolean isAccountNonLocked()                                  { return accountNonLocked; }
    @Override public boolean isCredentialsNonExpired()                             { return credentialsNonExpired; }
    @Override public Collection<? extends GrantedAuthority> getAuthorities()       { return authorities; }
}