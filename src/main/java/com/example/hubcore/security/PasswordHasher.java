package com.example.hubcore.security;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

public class PasswordHasher {

    private static final int ITERATIONS = 65536;
    private static final int KEY_LENGTH = 256;
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";

    private final SecureRandom random = new SecureRandom();

    public String hash(String password) throws Exception {
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        byte[] hash = deriveKey(password.toCharArray(), salt);
        return Base64.getEncoder().encodeToString(salt) + ":" +
               Base64.getEncoder().encodeToString(hash);
    }

    public boolean verify(String password, String stored) throws Exception {
        String[] parts = stored.split(":");
        if (parts.length != 2) return false;

        byte[] salt = Base64.getDecoder().decode(parts[0]);
        byte[] expectedHash = Base64.getDecoder().decode(parts[1]);
        byte[] candidate = deriveKey(password.toCharArray(), salt);

        if (candidate.length != expectedHash.length) return false;
        int result = 0;
        for (int i = 0; i < candidate.length; i++) {
            result |= candidate[i] ^ expectedHash[i];
        }
        return result == 0;
    }

    private byte[] deriveKey(char[] password, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, KEY_LENGTH);
        SecretKeyFactory skf = SecretKeyFactory.getInstance(ALGORITHM);
        return skf.generateSecret(spec).getEncoded();
    }
}
