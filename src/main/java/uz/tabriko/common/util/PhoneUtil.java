package uz.tabriko.common.util;

public final class PhoneUtil {

    private PhoneUtil() {
    }

    // Telegram sends phone numbers as digits only (no leading +); normalize both
    // submission and bot-side phones to "+" + digits so lookups by phone match.
    public static String normalize(String raw) {
        if (raw == null) return null;
        String digits = raw.replaceAll("[^0-9]", "");
        return "+" + digits;
    }
}
