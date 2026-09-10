package com.example.activity.service;

import com.example.activity.config.ServiceProperties;
import com.example.activity.ingestion.UpstreamException;
import org.springframework.stereotype.Service;
import javax.crypto.Cipher;
import javax.crypto.spec.*;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class TokenCipher {
    private final ServiceProperties properties;
    public TokenCipher(ServiceProperties properties) { this.properties = properties; }
    public void validateKey() { key(); }
    private SecretKeySpec key() {
        try {
            byte[] value = Base64.getDecoder().decode(properties.oauth().encryptionKey());
            if (value.length != 32) throw new IllegalArgumentException();
            return new SecretKeySpec(value, "AES");
        } catch (RuntimeException e) { throw new UpstreamException("OAuth requires a base64-encoded 32-byte encryption key"); }
    }
    public String encrypt(String value, String context) {
        try {
            byte[] iv = new byte[12]; new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(iv) + "." + Base64.getEncoder().encodeToString(cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (UpstreamException e) { throw e; }
        catch (Exception e) { throw new IllegalStateException("Token encryption failed", e); }
    }
    public String decrypt(String value, String context) {
        try {
            String[] parts = value.split("\\.", 2);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Base64.getDecoder().decode(parts[0])));
            cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(Base64.getDecoder().decode(parts[1])), StandardCharsets.UTF_8);
        } catch (Exception e) { throw new IllegalStateException("Token decryption failed"); }
    }
}
