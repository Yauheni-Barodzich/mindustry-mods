package scs;

/** Admin API constants. */
public final class AdminConstants {
    public static final String PLUGIN_NAME = "server-admin";
    public static final String CLIENT_NAME = "server-admin-client";
    public static final String ADMIN_VERSION = "1.0.0";
    public static final String API_PREFIX = "/api/v1/admin";
    /** Default port = gamePort + 2 (6567 -> 6569). */
    public static final int DEFAULT_PORT_OFFSET = 2;
    public static final String PASSWORD_FILE = "admin.password";

    private AdminConstants() {}
}
