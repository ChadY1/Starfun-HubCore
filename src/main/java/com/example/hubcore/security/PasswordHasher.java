package com.example.hubcore.security;

import com.example.hubcore.HubCorePlugin;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

public class PasswordHasher {

    private static final int KEY_LENGTH = 256; // bits
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";

    private final SecureRandom random = new SecureRandom();
    private final String pepper;
    private final int iterations;

    public PasswordHasher(HubCorePlugin plugin) {
        this.pepper = plugin.getConfig().getString("security.password.pepper", "");
        this.iterations = plugin.getConfig().getInt("security.password.iterations", 65536);

        if (pepper == null || pepper.isEmpty()) {
            plugin.getLogger().warning("[Starfun/Auth] security.password.pepper is empty, set a strong pepper.");
        }
        if (iterations < 20000) {
            plugin.getLogger().warning("[Starfun/Auth] security.password.iterations is low (" + iterations + "), consider >= 60000.");
        }
    }

    public String hash(String password) throws Exception {
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        byte[] hash = deriveKey((password + pepper).toCharArray(), salt);
        return Base64.getEncoder().encodeToString(salt) + ":" +
               Base64.getEncoder().encodeToString(hash);
    }

    public boolean verify(String password, String stored) throws Exception {
        String[] parts = stored.split(":");
        if (parts.length != 2) return false;

        byte[] salt = Base64.getDecoder().decode(parts[0]);
        byte[] expectedHash = Base64.getDecoder().decode(parts[1]);
        byte[] candidate = deriveKey((password + pepper).toCharArray(), salt);

        if (candidate.length != expectedHash.length) return false;
        int result = 0;
        for (int i = 0; i < candidate.length; i++) {
            result |= candidate[i] ^ expectedHash[i];
        }
        return result == 0;
    }

    private byte[] deriveKey(char[] password, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, KEY_LENGTH);
        SecretKeyFactory skf = SecretKeyFactory.getInstance(ALGORITHM);
        return skf.generateSecret(spec).getEncoded();
    }
}
