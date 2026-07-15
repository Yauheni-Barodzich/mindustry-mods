package scs;

/** Unified SCS Suite mod identity and infra-mod detection. */
public final class ScsConstants {
    public static final String MOD_NAME = "server-sync-admin";

    private static final String[] INFRA_MOD_NAMES = {
            MOD_NAME,
            "scs",
            "server-content-sync",
            "server-content-sync-client",
            "server-admin",
            "server-admin-client"
    };

    private static final String[] PROTECTED_FILE_MARKERS = {
            MOD_NAME,
            "sync-admin",
            "scs",
            "server-admin",
            "server-content-sync"
    };

    private ScsConstants() {}

    public static boolean isInfraModName(String name) {
        if (name == null) return false;
        String lower = name.toLowerCase();
        for (String infra : INFRA_MOD_NAMES) {
            if (lower.equals(infra)) return true;
        }
        return false;
    }

    public static boolean isProtectedModFile(String fileName) {
        if (fileName == null) return false;
        String lower = fileName.toLowerCase();
        for (String marker : PROTECTED_FILE_MARKERS) {
            if (lower.contains(marker)) return true;
        }
        return false;
    }
}
