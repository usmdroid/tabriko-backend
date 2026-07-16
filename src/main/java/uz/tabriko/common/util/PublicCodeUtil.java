package uz.tabriko.common.util;

import java.security.SecureRandom;

public final class PublicCodeUtil {

    private static final String CHARS = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final int LENGTH = 7;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PublicCodeUtil() {}

    public static String generate() {
        StringBuilder sb = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            sb.append(CHARS.charAt(RANDOM.nextInt(CHARS.length())));
        }
        return sb.toString();
    }
}
