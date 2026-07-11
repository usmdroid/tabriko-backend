package uz.tabriko.common.util;

public final class VersionUtil {
    private VersionUtil() {}

    public static int compare(String v1, String v2) {
        String[] parts1 = (v1 == null ? "" : v1).split("\\.", -1);
        String[] parts2 = (v2 == null ? "" : v2).split("\\.", -1);
        int len = Math.max(parts1.length, parts2.length);
        for (int i = 0; i < len; i++) {
            int n1 = parseComponent(i < parts1.length ? parts1[i] : "0");
            int n2 = parseComponent(i < parts2.length ? parts2[i] : "0");
            if (n1 != n2) return Integer.compare(n1, n2);
        }
        return 0;
    }

    // minVersion inclusive, maxVersion exclusive
    public static boolean isInRange(String version, String minVersion, String maxVersion) {
        if (version == null || version.isBlank()) return false;
        if (minVersion != null && !minVersion.isBlank() && compare(version, minVersion) < 0) return false;
        if (maxVersion != null && !maxVersion.isBlank() && compare(version, maxVersion) >= 0) return false;
        return true;
    }

    private static int parseComponent(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
