package com.example.activity.service;

import com.example.activity.TestSupport;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class TokenCipherTest {
    TokenCipher cipher = new TokenCipher(TestSupport.properties());
    @Test void encryptsWithRandomNonceAndAuthenticatesContext() {
        String encrypted = cipher.encrypt("secret-token", "rider:garmin");
        assertThat(encrypted).doesNotContain("secret-token").isNotEqualTo(cipher.encrypt("secret-token", "rider:garmin"));
        assertThat(cipher.decrypt(encrypted, "rider:garmin")).isEqualTo("secret-token");
        assertThatThrownBy(() -> cipher.decrypt(encrypted, "attacker:garmin")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> cipher.decrypt("invalid", "rider:garmin")).isInstanceOf(IllegalStateException.class);
    }
}
