package com.djmahirnationtv.status.backend.auth;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
public class AuthService implements UserDetailsService {
    private final UserRepository users;
    private final PasswordEncoder passwords;

    public AuthService(UserRepository users, PasswordEncoder passwords) {
        this.users = users;
        this.passwords = passwords;
    }

    public AppUser register(String email, String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be at most 72 UTF-8 bytes.");
        }
        try {
            return users.saveAndFlush(new AppUser(normalizeEmail(email), passwords.encode(password), "local", null));
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Unable to register this email. Try signing in.");
        }
    }

    @Override
    public UserDetails loadUserByUsername(String email) {
        AppUser user = users.findByEmail(normalizeEmail(email))
                .filter(account -> account.getPasswordHash() != null)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid email or password."));
        return User.withUsername(user.getId().toString())
                .password(user.getPasswordHash())
                .roles("USER")
                .build();
    }

    public AppUser getUser(String id) {
        return users.findById(Long.valueOf(id))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please sign in again."));
    }

    public AppUser oauthUser(String provider, String providerId, String email) {
        var existing = users.findByProviderAndProviderId(provider, providerId);
        if (existing.isPresent()) {
            return existing.get();
        }
        try {
            return users.saveAndFlush(new AppUser(normalizeEmail(email), null, provider, providerId));
        } catch (DataIntegrityViolationException exception) {
            return users.findByProviderAndProviderId(provider, providerId)
                    .orElseThrow(() -> new OAuth2AuthenticationException(new OAuth2Error("account_exists")));
        }
    }

    private String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
