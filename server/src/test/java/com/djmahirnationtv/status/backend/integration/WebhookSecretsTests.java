package com.djmahirnationtv.status.backend.integration;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class WebhookSecretsTests {
    @Test
    void encryptsWithDifferentNoncesAndDecrypts() {
        var secrets = new WebhookSecrets("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
        String first = secrets.encrypt("test secret");
        assertThat(first).isNotEqualTo(secrets.encrypt("test secret"));
        assertThat(secrets.decrypt(first)).isEqualTo("test secret");
        assertThatThrownBy(() -> secrets.decrypt("invalid")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void missingKeyDisablesIntegrationsAndInvalidKeysAreRejected() {
        var secrets = new WebhookSecrets("");
        assertThat(secrets.configured()).isFalse();
        assertThatThrownBy(() -> secrets.encrypt("secret")).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThatThrownBy(() -> new WebhookSecrets("c2hvcnQ=")).isInstanceOf(IllegalArgumentException.class);
    }
}
