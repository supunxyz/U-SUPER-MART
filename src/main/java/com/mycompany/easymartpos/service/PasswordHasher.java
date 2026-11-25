package com.mycompany.easymartpos.service;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** PBKDF2 password storage with support for upgrading existing plaintext rows at sign-in. */
public final class PasswordHasher {
    private static final int ITERATIONS = 210_000;
    private static final int KEY_BITS = 256;
    private static final String PREFIX = "pbkdf2-sha256";
    private PasswordHasher() { }

    public static String hash(char[] password) {
        requireStrong(password);
        return hashUnchecked(password);
    }

    /** Existing short plaintext passwords can be migrated without forcing an immediate reset. */
    static String hashLegacy(char[] password) {
        return hashUnchecked(password);
    }

    private static String hashUnchecked(char[] password) {
        byte[] salt = new byte[16]; new SecureRandom().nextBytes(salt);
        byte[] key = derive(password, salt, ITERATIONS);
        try { return PREFIX + "$" + ITERATIONS + "$" + Base64.getEncoder().encodeToString(salt)
                + "$" + Base64.getEncoder().encodeToString(key); }
        finally { Arrays.fill(key, (byte) 0); }
    }

    public static boolean verify(char[] password, String stored) {
        if (password == null || stored == null) return false;
        if (!stored.startsWith(PREFIX + "$")) {
            // Previous installations stored plaintext. A successful login rewrites that row as a hash.
            return MessageDigest.isEqual(new String(password).getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    stored.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        try {
            String[] parts = stored.split("\\$", -1);
            if (parts.length != 4) return false;
            int iterations = Integer.parseInt(parts[1]);
            if (iterations < 100_000 || iterations > 1_000_000) return false;
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            if (salt.length != 16 || expected.length != KEY_BITS / 8) return false;
            byte[] actual = derive(password, salt, iterations);
            try { return MessageDigest.isEqual(actual, expected); }
            finally { Arrays.fill(actual, (byte) 0); }
        } catch (IllegalArgumentException exception) { return false; }
    }

    public static boolean isHashed(String stored) { return stored != null && stored.startsWith(PREFIX + "$"); }

    public static void requireStrong(char[] password) {
        if (password == null || password.length < 12 || password.length > 128)
            throw new IllegalArgumentException("Password must contain 12 to 128 characters.");
    }

    private static byte[] derive(char[] password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, KEY_BITS);
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); }
        catch (Exception exception) { throw new IllegalStateException("Password hashing is unavailable.", exception); }
        finally { spec.clearPassword(); }
    }
}
