package com.mycompany.easymartpos;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Bundled MariaDB that lets the installed app run on a PC with nothing else installed.
 * The server binaries ship next to the application (folder "mariadb"); the data lives in the
 * user's profile so upgrades and reinstalls never touch it. It is only used when the
 * EASYMART_DB_URL setting is absent, so an external MySQL server can still be configured.
 */
final class EmbeddedDatabase {
    static final int PORT = 33307;
    static final String DATABASE = "easymart_pos";
    private static final int INSTANCE_LOCK_PORT = 33308;
    private static final List<String> MIGRATIONS = List.of("V1__core_pos.sql", "V2__sale_loyalty.sql", "V3__operational_pos_schema.sql", "V4__customer_archive.sql");
    private static final List<String> REQUIRED_TABLES = List.of("categories", "customers", "products", "sales", "sale_items",
            "sale_payments", "stock_movements", "sale_loyalty", "users", "suppliers", "suppliers_purchases",
            "purchase_items", "product_batches", "expenses", "promotions", "loyalty_cards", "cashier_shifts",
            "sale_item_batch_allocations");

    private static volatile DBConnection.DatabaseConfiguration active;
    private static Process serverProcess;
    private static ServerSocket instanceLock;

    private EmbeddedDatabase() { }

    static Optional<DBConnection.DatabaseConfiguration> configuration() {
        return Optional.ofNullable(active);
    }

    /** True when the bundled server exists and no external database was configured. */
    static boolean isAvailable() {
        return externalConfigured() == false && bundleHome() != null;
    }

    static Path binDirectory() {
        Path home = bundleHome();
        return home == null ? null : home.resolve("bin");
    }

    /** Returns false when another copy of the application already holds the lock. */
    static boolean acquireSingleInstance() {
        try {
            instanceLock = new ServerSocket(INSTANCE_LOCK_PORT, 1, InetAddress.getLoopbackAddress());
            return true;
        } catch (IOException exception) {
            return false;
        }
    }

    static synchronized void start(Consumer<String> progress) throws Exception {
        if (active != null || !isAvailable()) return;
        Path home = bundleHome();
        Path root = dataRoot();
        Path data = root.resolve("data");
        Path settings = root.resolve("db.properties");
        Files.createDirectories(root);

        if (!Files.isDirectory(data.resolve("mysql")) || !Files.isRegularFile(settings)) {
            progress.accept("Preparing the database for first use...");
            initialise(home, root, data, settings);
        }
        String password = readPassword(settings);
        String rootUrl = "jdbc:mysql://127.0.0.1:" + PORT + "/?sslMode=DISABLED&allowPublicKeyRetrieval=true";

        if (!canConnect(rootUrl, password)) {
            progress.accept("Starting the database...");
            launchServer(home, data, root);
            waitUntilReady(rootUrl, password);
        }
        progress.accept("Checking the database...");
        prepareSchema(home, rootUrl, password);
        active = new DBConnection.DatabaseConfiguration(
                "jdbc:mysql://127.0.0.1:" + PORT + "/" + DATABASE + "?sslMode=DISABLED&allowPublicKeyRetrieval=true", "root", password);
    }

    private static void initialise(Path home, Path root, Path data, Path settings) throws Exception {
        deleteRecursively(data);
        Files.deleteIfExists(settings);
        String password = randomPassword();
        Files.createDirectories(root);
        List<String> command = List.of(home.resolve("bin").resolve("mariadb-install-db.exe").toString(),
                "--datadir=" + data, "--password=" + password);
        Process process = new ProcessBuilder(command).redirectErrorStream(true)
                .redirectOutput(root.resolve("install.log").toFile()).start();
        if (!process.waitFor(3, TimeUnit.MINUTES) || process.exitValue() != 0) {
            process.destroyForcibly();
            deleteRecursively(data);
            throw new IllegalStateException("The database could not be created. See " + root.resolve("install.log"));
        }
        Properties properties = new Properties();
        properties.setProperty("password", password);
        try (var out = Files.newOutputStream(settings)) { properties.store(out, "EasyMart POS local database"); }
    }

    private static void launchServer(Path home, Path data, Path root) throws IOException {
        List<String> command = List.of(home.resolve("bin").resolve("mariadbd.exe").toString(), "--no-defaults",
                "--basedir=" + home, "--datadir=" + data, "--port=" + PORT, "--bind-address=127.0.0.1",
                "--character-set-server=utf8mb4", "--collation-server=utf8mb4_general_ci",
                "--innodb-buffer-pool-size=64M", "--skip-name-resolve", "--max-connections=60");
        serverProcess = new ProcessBuilder(command).redirectErrorStream(true)
                .redirectOutput(root.resolve("server.log").toFile()).start();
        Runtime.getRuntime().addShutdownHook(new Thread(EmbeddedDatabase::stop, "easymart-db-shutdown"));
    }

    private static void waitUntilReady(String url, String password) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(90);
        while (System.nanoTime() < deadline) {
            if (serverProcess != null && !serverProcess.isAlive()) {
                throw new IllegalStateException("The database stopped unexpectedly (exit code " + serverProcess.exitValue() + "). Another program may be using port " + PORT + ".");
            }
            if (canConnect(url, password)) return;
            Thread.sleep(400);
        }
        throw new IllegalStateException("The database did not start in time.");
    }

    private static void prepareSchema(Path home, String rootUrl, String password) throws Exception {
        try (Connection connection = DriverManager.getConnection(rootUrl, "root", password); Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE IF NOT EXISTS " + DATABASE + " CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci");
        }
        String url = "jdbc:mysql://127.0.0.1:" + PORT + "/" + DATABASE + "?sslMode=DISABLED&allowPublicKeyRetrieval=true";
        try (Connection connection = DriverManager.getConnection(url, "root", password);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS schema_migrations (version VARCHAR(100) PRIMARY KEY, applied_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP)");
            for (String migration : MIGRATIONS) {
                if (migrationApplied(connection, migration)) continue;
                runMigration(home, password, migration);
                verifyMigration(connection, migration);
                try (PreparedStatement record = connection.prepareStatement("INSERT INTO schema_migrations (version) VALUES (?)")) {
                    record.setString(1, migration); record.executeUpdate();
                }
            }
            verifySchema(connection);
        }
    }

    private static boolean migrationApplied(Connection connection, String name) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("SELECT 1 FROM schema_migrations WHERE version=?")) {
            query.setString(1, name);
            try (ResultSet result = query.executeQuery()) { return result.next(); }
        }
    }

    private static void verifyMigration(Connection connection, String name) throws SQLException {
        if (name.startsWith("V1") && !hasTable(connection, "sale_payments")) throw new SQLException("V1 did not create sale_payments.");
        if (name.startsWith("V2") && !hasTable(connection, "sale_loyalty")) throw new SQLException("V2 did not create sale_loyalty.");
        if (name.startsWith("V3") && !hasTable(connection, "product_batches")) throw new SQLException("V3 did not create product_batches.");
        if (name.startsWith("V4") && !hasColumn(connection, "customers", "active")) throw new SQLException("V4 did not add customers.active.");
    }

    private static void verifySchema(Connection connection) throws SQLException {
        for (String table : REQUIRED_TABLES) if (!hasTable(connection, table))
            throw new SQLException("Database setup is incomplete (missing table " + table + ").");
        for (String tableColumn : List.of("customers.active", "customers.loyalty_points", "products.retail_price",
                "sales.shift_id", "sale_items.cost_price", "sale_payments.transfer_reference", "sale_payments.bank_name")) {
            String[] parts = tableColumn.split("\\.");
            if (!hasColumn(connection, parts[0], parts[1])) throw new SQLException("Database setup is incomplete (missing " + tableColumn + ").");
        }
    }

    private static boolean hasTable(Connection connection, String table) throws SQLException {
        try (ResultSet rows = connection.getMetaData().getTables(connection.getCatalog(), null, table, new String[]{"TABLE"})) { return rows.next(); }
    }

    private static boolean hasColumn(Connection connection, String table, String column) throws SQLException {
        try (ResultSet rows = connection.getMetaData().getColumns(connection.getCatalog(), null, table, column)) { return rows.next(); }
    }

    private static void runMigration(Path home, String password, String name) throws Exception {
        Path script = Files.createTempFile("easymart-migration-", ".sql");
        Path options = Files.createTempFile("easymart-client-", ".cnf");
        try (InputStream in = EmbeddedDatabase.class.getResourceAsStream("/db/migration/" + name)) {
            if (in == null) throw new IllegalStateException("Missing database script " + name);
            Files.copy(in, script, StandardCopyOption.REPLACE_EXISTING);
            Files.writeString(options, "[client]\nhost=127.0.0.1\nport=" + PORT + "\nuser=root\npassword=\"" + password + "\"\n", StandardCharsets.UTF_8);
            List<String> command = new ArrayList<>(List.of(home.resolve("bin").resolve("mariadb.exe").toString(),
                    "--defaults-extra-file=" + options, "--default-character-set=utf8mb4", DATABASE));
            Path log = dataRoot().resolve("migration-" + name + ".log");
            Process process = new ProcessBuilder(command).redirectInput(script.toFile())
                    .redirectErrorStream(true).redirectOutput(log.toFile()).start();
            if (!process.waitFor(3, TimeUnit.MINUTES)) { process.destroyForcibly(); throw new IllegalStateException("Database setup timed out."); }
            if (process.exitValue() != 0) throw new IllegalStateException("Database migration " + name + " failed. See " + log);
        } finally {
            Files.deleteIfExists(script); Files.deleteIfExists(options);
        }
    }

    private static boolean columnExists(String url, String password, String table, String column) throws SQLException {
        try (Connection connection = DriverManager.getConnection(url, "root", password);
             var statement = connection.prepareStatement("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = ? AND table_name = ? AND column_name = ?")) {
            statement.setString(1, DATABASE); statement.setString(2, table); statement.setString(3, column);
            try (ResultSet result = statement.executeQuery()) { result.next(); return result.getInt(1) > 0; }
        }
    }

    private static boolean tableExists(String url, String password, String table) throws SQLException {
        try (Connection connection = DriverManager.getConnection(url, "root", password);
             var statement = connection.prepareStatement("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = ? AND table_name = ?")) {
            statement.setString(1, DATABASE); statement.setString(2, table);
            try (ResultSet result = statement.executeQuery()) { result.next(); return result.getInt(1) > 0; }
        }
    }

    private static boolean canConnect(String url, String password) {
        try (Connection ignored = DriverManager.getConnection(url, "root", password)) { return true; }
        catch (SQLException exception) { return false; }
    }

    private static void stop() {
        Process process = serverProcess;
        if (process == null || !process.isAlive()) return;
        try {
            Path settings = dataRoot().resolve("db.properties");
            try (Connection connection = DriverManager.getConnection("jdbc:mysql://127.0.0.1:" + PORT + "/?sslMode=DISABLED&allowPublicKeyRetrieval=true", "root", readPassword(settings));
                 Statement statement = connection.createStatement()) {
                statement.execute("SHUTDOWN");
            }
            process.waitFor(20, TimeUnit.SECONDS);
        } catch (Exception ignored) { }
        if (process.isAlive()) process.destroyForcibly();
    }

    private static boolean externalConfigured() {
        String property = System.getProperty("EASYMART_DB_URL");
        String environment = System.getenv("EASYMART_DB_URL");
        return (property != null && !property.isBlank()) || (environment != null && !environment.isBlank());
    }

    private static Path bundleHome() {
        String override = System.getenv("EASYMART_MARIADB_HOME");
        if (override != null && !override.isBlank() && Files.isRegularFile(Path.of(override, "bin", "mariadbd.exe"))) return Path.of(override);
        List<Path> candidates = new ArrayList<>();
        // NetBeans and Maven run compiled classes from the project working directory.
        candidates.add(Path.of("bundle", "mariadb").toAbsolutePath());
        String launcher = System.getProperty("jpackage.app-path");
        if (launcher != null && !launcher.isBlank()) candidates.add(Path.of(launcher).toAbsolutePath().getParent().resolve("mariadb"));
        try {
            Path jar = Path.of(EmbeddedDatabase.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (jar.getParent() != null && jar.getParent().getParent() != null) candidates.add(jar.getParent().getParent().resolve("mariadb"));
        } catch (Exception ignored) { }
        for (Path candidate : candidates) if (Files.isRegularFile(candidate.resolve("bin").resolve("mariadbd.exe"))) return candidate;
        return null;
    }

    private static Path dataRoot() {
        String base = System.getenv("LOCALAPPDATA");
        Path parent = base == null || base.isBlank() ? Path.of(System.getProperty("user.home"), "AppData", "Local") : Path.of(base);
        return parent.resolve("UshanthaSuperMartPOS");
    }

    private static String readPassword(Path settings) throws IOException {
        Properties properties = new Properties();
        try (var in = Files.newInputStream(settings)) { properties.load(in); }
        return properties.getProperty("password");
    }

    private static String randomPassword() {
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
        SecureRandom random = new SecureRandom();
        StringBuilder value = new StringBuilder();
        for (int i = 0; i < 28; i++) value.append(alphabet.charAt(random.nextInt(alphabet.length())));
        return value.toString();
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) return;
        try (var walk = Files.walk(path)) {
            for (Path entry : (Iterable<Path>) walk.sorted(java.util.Comparator.reverseOrder())::iterator) Files.deleteIfExists(entry);
        }
    }
}
