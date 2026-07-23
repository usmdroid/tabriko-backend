package uz.tabriko.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Deterministic, unsalted SHA-256 hash of a normalized E.164 phone number.
 *
 * Algorithm (must be reproduced identically on the mobile client):
 *   1. Normalize: strip all non-digit characters, then prepend '+'.
 *      e.g. "998 90 123 45 67" → "+998901234567"
 *   2. Hash: SHA-256 of the UTF-8 bytes of the normalized string.
 *   3. Encode: lowercase hex string (64 characters).
 *
 * No salt, no HMAC. Input is always the already-normalized phone stored in the DB.
 */
public final class PhoneHashUtil {

    private PhoneHashUtil() {
    }

    public static String hash(String normalizedPhone) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(normalizedPhone.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
