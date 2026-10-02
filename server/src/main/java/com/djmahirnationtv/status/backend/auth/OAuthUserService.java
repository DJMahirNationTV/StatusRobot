package com.djmahirnationtv.status.backend.auth;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
public class OAuthUserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {
    private final AuthService auth;
    private final RestClient client;

    public OAuthUserService(AuthService auth, RestClient client) {
        this.auth = auth;
        this.client = client;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest request) {
        String provider = request.getClientRegistration().getRegistrationId();
        String accessToken = request.getAccessToken().getTokenValue();
        try {
            Map<String, Object> profile = client.get()
                    .uri(request.getClientRegistration().getProviderDetails().getUserInfoEndpoint().getUri())
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .header("Accept", "application/json")
                    .header("User-Agent", "StatusRobot")
                    .retrieve().body(new ParameterizedTypeReference<>() {});

            if (profile == null || profile.get("id") == null) {
                throw failure("invalid_profile");
            }
            String email;
            if (provider.equals("github")) {
                GitHubEmail[] emails = client.get().uri("https://api.github.com/user/emails")
                        .headers(headers -> headers.setBearerAuth(accessToken))
                        .header("Accept", "application/vnd.github+json")
                        .header("User-Agent", "StatusRobot")
                        .retrieve().body(GitHubEmail[].class);
                email = emails == null ? null : Arrays.stream(emails)
                        .filter(address -> address.primary() && address.verified())
                        .map(GitHubEmail::email).findFirst().orElse(null);
            } else if (provider.equals("discord") && Boolean.TRUE.equals(profile.get("verified"))) {
                email = (String) profile.get("email");
            } else {
                throw failure("email_unverified");
            }
            if (email == null || email.isBlank()) {
                throw failure("email_unverified");
            }

            AppUser user = auth.oauthUser(provider, profile.get("id").toString(), email);
            return new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("ROLE_USER")),
                    Map.of("id", user.getId().toString(), "email", user.getEmail()), "id");
        } catch (RestClientException exception) {
            throw failure("provider_unavailable");
        }
    }

    private OAuth2AuthenticationException failure(String code) {
        return new OAuth2AuthenticationException(new OAuth2Error(code));
    }

    public record GitHubEmail(String email, boolean primary, boolean verified) {
    }
}
