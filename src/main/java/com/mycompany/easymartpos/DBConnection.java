package com.mycompany.easymartpos;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DBConnection {

    private static final String DEFAULT_URL = "jdbc:mysql://localhost:3306/easymart_pos";

    public static Connection getConnection() throws SQLException {
        DatabaseConfiguration configuration = configuration();
        return DriverManager.getConnection(configuration.url(), configuration.user(), configuration.password());
    }

    /** Package-private reuse of the exact resolved connection settings for trusted local tooling. */
    static DatabaseConfiguration configuration() {
        String url = setting("EASYMART_DB_URL", null);
        if (url == null) {
            var embedded = EmbeddedDatabase.configuration();
            if (embedded.isPresent()) return embedded.get();
            url = DEFAULT_URL;
        }
        return new DatabaseConfiguration(url, setting("EASYMART_DB_USER", "root"), setting("EASYMART_DB_PASSWORD", ""));
    }

    record DatabaseConfiguration(String url, String user, String password) { }

    private static String setting(String name, String fallback) {
        String property = System.getProperty(name);
        if (property != null && !property.isBlank()) {
            return property;
        }
        String environment = System.getenv(name);
        return environment == null || environment.isBlank() ? fallback : environment;
    }
}
