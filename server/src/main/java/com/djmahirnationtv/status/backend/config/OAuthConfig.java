package com.djmahirnationtv.status.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class OAuthConfig {
    @Bean
    public ClientRegistrationRepository clientRegistrations(Environment environment) {
        Map<String, ClientRegistration> registrations = new HashMap<>();
        String redirectBase = environment.getRequiredProperty("app.oauth.redirect-base-url");

        for (String provider : new String[]{"github", "discord"}) {
            String clientId = environment.getProperty("app.oauth." + provider + ".client-id", "");
            String clientSecret = environment.getProperty("app.oauth." + provider + ".client-secret", "");
            if (clientId.isBlank() || clientSecret.isBlank()) {
                continue;
            }

            ClientRegistration.Builder builder = provider.equals("github")
                    ? CommonOAuth2Provider.GITHUB.getBuilder("github").scope("read:user", "user:email")
                    : ClientRegistration.withRegistrationId("discord")
                            .clientName("Discord")
                            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                            .authorizationUri("https://discord.com/oauth2/authorize")
                            .tokenUri("https://discord.com/api/oauth2/token")
                            .userInfoUri("https://discord.com/api/users/@me")
                            .userNameAttributeName("id")
                            .scope("identify", "email");

            registrations.put(provider, builder.clientId(clientId)
                    .clientSecret(clientSecret)
                    .redirectUri(redirectBase + "/login/oauth2/code/" + provider)
                    .build());
        }
        return registrations::get;
    }
}
