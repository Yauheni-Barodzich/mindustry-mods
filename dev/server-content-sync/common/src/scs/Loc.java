package scs;

import arc.*;

/** Bundle helper with English fallback. Supports {0}/{1} placeholders. */
public final class Loc {
    private Loc() {}

    public static String get(String key, String fallback) {
        try {
            if (Core.bundle != null && Core.bundle.has(key)) {
                return Core.bundle.get(key);
            }
        } catch (Throwable ignored) {
        }
        return fallback;
    }

    public static String format(String key, String fallback, Object... args) {
        String fmt = get(key, fallback);
        if (args == null || args.length == 0) return fmt;
        try {
            if (Core.bundle != null && Core.bundle.has(key)) {
                return Core.bundle.format(key, args);
            }
        } catch (Throwable ignored) {
        }
        String out = fmt;
        for (int i = 0; i < args.length; i++) {
            out = out.replace("{" + i + "}", String.valueOf(args[i]));
        }
        return out;
    }
}
