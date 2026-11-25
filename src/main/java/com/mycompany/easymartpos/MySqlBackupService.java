package com.mycompany.easymartpos;

import com.mycompany.easymartpos.service.Authorization;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** Local, admin-only MySQL command-line backup and restore support. Never logs credentials. */
public final class MySqlBackupService {
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss");
    private static final long TOOL_TIMEOUT_MINUTES = 15;

    public ToolPaths discoverTools() {
        DBConnection.DatabaseConfiguration settings = DBConnection.configuration();
        Path dump = findTool("mysqldump");
        Path mysql = findTool("mysql");
        if (dump == null || mysql == null) throw new IllegalStateException("MySQL backup tools could not be located. Install MySQL Server tools or add its bin folder to PATH.");
        return new ToolPaths(dump, mysql, parse(settings).database());
    }

    public BackupResult backup(Path destination) throws Exception {
        Authorization.requireAdmin();
        validateDestination(destination);
        ToolPaths tools = discoverTools();
        return runDump(tools, destination);
    }

    public RestoreResult restore(Path selectedBackup, Path safetyBackupDirectory) throws Exception {
        Authorization.requireAdmin();
        validateRestoreFile(selectedBackup);
        ToolPaths tools = discoverTools();
        if (safetyBackupDirectory == null || !Files.isDirectory(safetyBackupDirectory)) throw new IllegalArgumentException("Choose a valid folder for the automatic safety backup.");
        Path safetyBackup = uniqueSafetyBackupPath(safetyBackupDirectory);
        BackupResult safety;
        try {
            safety = runDump(tools, safetyBackup); // Never restore without a verified safety backup.
        } catch (Exception exception) {
            throw new IllegalStateException("Automatic safety backup failed. Restore was cancelled: " + exception.getMessage(), exception);
        }
        runRestore(tools, selectedBackup);
        verifyImportantTables();
        return new RestoreResult(selectedBackup, safety.path());
    }

    public static void validateRestoreFile(Path file) {
        if (file == null || !Files.isRegularFile(file) || !Files.isReadable(file)) throw new IllegalArgumentException("Select a readable SQL backup file.");
        try { if (Files.size(file) == 0) throw new IllegalArgumentException("The selected SQL backup file is empty."); }
        catch (IOException exception) { throw new IllegalArgumentException("Cannot read the selected SQL backup file."); }
    }

    private BackupResult runDump(ToolPaths tools, Path destination) throws Exception {
        validateDestination(destination);
        DBConnection.DatabaseConfiguration settings = DBConnection.configuration();
        Path credentials = writeClientOptionFile(settings);
        Path errors = Files.createTempFile("easymart-mysqldump-", ".err");
        try {
            List<String> command = new ArrayList<>();
            command.add(tools.mysqldump().toString());
            command.add("--defaults-extra-file=" + credentials.toAbsolutePath()); // Must be the first mysqldump option.
            command.add("--single-transaction"); command.add("--routines"); command.add("--events"); command.add("--triggers");
            command.add("--add-drop-table"); command.add("--no-tablespaces"); command.add("--databases"); command.add(tools.database());
            ProcessBuilder process = new ProcessBuilder(command); process.redirectOutput(destination.toFile()); process.redirectError(errors.toFile());
            run(process, errors, settings.password(), "Backup");
            if (!Files.isRegularFile(destination) || Files.size(destination) == 0) throw new IllegalStateException("Backup finished without creating a usable SQL file.");
            return new BackupResult(destination.toAbsolutePath(), LocalDateTime.now(), Files.size(destination));
        } catch (Exception exception) {
            try { if (Files.exists(destination) && Files.size(destination) == 0) Files.deleteIfExists(destination); } catch (IOException ignored) { }
            throw exception;
        } finally {
            deleteQuietly(credentials); deleteQuietly(errors);
        }
    }

    private void runRestore(ToolPaths tools, Path source) throws Exception {
        DBConnection.DatabaseConfiguration settings = DBConnection.configuration();
        Path credentials = writeClientOptionFile(settings);
        Path errors = Files.createTempFile("easymart-mysql-restore-", ".err");
        try {
            List<String> command = List.of(tools.mysql().toString(), "--defaults-extra-file=" + credentials.toAbsolutePath(), tools.database());
            ProcessBuilder process = new ProcessBuilder(command); process.redirectInput(source.toFile()); process.redirectOutput(ProcessBuilder.Redirect.DISCARD); process.redirectError(errors.toFile());
            run(process, errors, settings.password(), "Restore");
        } finally {
            deleteQuietly(credentials); deleteQuietly(errors);
        }
    }

    private void run(ProcessBuilder process, Path errorFile, String password, String operation) throws Exception {
        Process running = process.start();
        if (!running.waitFor(TOOL_TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
            running.destroyForcibly();
            throw new IllegalStateException(operation + " timed out. No success was reported.");
        }
        if (running.exitValue() != 0) throw new IllegalStateException(operation + " failed (MySQL tool exit code " + running.exitValue() + "): " + safeDiagnostic(errorFile, password));
    }

    private void verifyImportantTables() throws SQLException {
        try (Connection connection = DBConnection.getConnection()) {
            for (String table : List.of("users", "products", "sales")) {
                try (PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM " + table + " LIMIT 1")) { statement.executeQuery(); }
            }
        }
    }

    private Path findTool(String baseName) {
        String executable = System.getProperty("os.name", "").toLowerCase().contains("win") ? baseName + ".exe" : baseName;
        Set<Path> directories = new LinkedHashSet<>();
        Path bundled = EmbeddedDatabase.binDirectory();
        if (bundled != null) directories.add(bundled);
        String path = System.getenv("PATH");
        if (path != null) for (String entry : path.split(java.util.regex.Pattern.quote(java.io.File.pathSeparator))) if (!entry.isBlank()) directories.add(Path.of(entry));
        for (String variable : List.of("ProgramFiles", "ProgramFiles(x86)")) {
            String root = System.getenv(variable); if (root != null && !root.isBlank()) addCommonMySqlDirectories(directories, Path.of(root));
        }
        directories.add(Path.of("C:\\Program Files\\MySQL\\MySQL Server 8.0\\bin")); directories.add(Path.of("C:\\Program Files\\MySQL\\MySQL Server 5.7\\bin"));
        directories.add(Path.of("C:\\xampp\\mysql\\bin")); directories.add(Path.of("C:\\wamp64\\bin\\mysql"));
        for (Path directory : directories) {
            Path candidate = directory.resolve(executable); if (Files.isRegularFile(candidate)) return candidate.toAbsolutePath();
            if (directory.getFileName() != null && directory.getFileName().toString().equalsIgnoreCase("mysql")) {
                try (var versions = Files.list(directory)) {
                    Path found = versions.map(version -> version.resolve("bin").resolve(executable)).filter(Files::isRegularFile).findFirst().orElse(null);
                    if (found != null) return found.toAbsolutePath();
                } catch (IOException ignored) { }
            }
        }
        return null;
    }

    private void addCommonMySqlDirectories(Set<Path> directories, Path programFiles) {
        Path mysqlRoot = programFiles.resolve("MySQL"); directories.add(mysqlRoot.resolve("MySQL Server 8.0").resolve("bin")); directories.add(mysqlRoot.resolve("MySQL Server 5.7").resolve("bin"));
        if (!Files.isDirectory(mysqlRoot)) return;
        try (var children = Files.list(mysqlRoot)) { children.filter(Files::isDirectory).forEach(child -> directories.add(child.resolve("bin"))); }
        catch (IOException ignored) { }
    }

    private Path writeClientOptionFile(DBConnection.DatabaseConfiguration settings) throws IOException {
        ConnectionTarget target = parse(settings);
        Path file = Files.createTempFile("easymart-mysql-client-", ".cnf");
        String content = "[client]\nhost=" + option(target.host()) + "\nport=" + target.port() + "\nuser=" + option(settings.user()) + "\npassword=" + option(settings.password()) + "\n";
        Files.writeString(file, content, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
        return file;
    }

    private ConnectionTarget parse(DBConnection.DatabaseConfiguration settings) {
        try {
            URI uri = URI.create(settings.url().substring("jdbc:".length()));
            String database = uri.getPath() == null ? "" : uri.getPath().replaceFirst("^/", "");
            if (database.isBlank()) throw new IllegalArgumentException("The configured JDBC URL does not include a database name.");
            return new ConnectionTarget(uri.getHost() == null ? "localhost" : uri.getHost(), uri.getPort() < 0 ? 3306 : uri.getPort(), database);
        } catch (Exception exception) { throw new IllegalStateException("The configured MySQL connection URL cannot be used by MySQL backup tools."); }
    }

    private void validateDestination(Path destination) {
        if (destination == null) throw new IllegalArgumentException("Choose where to save the SQL backup.");
        Path parent = destination.toAbsolutePath().getParent(); if (parent == null || !Files.isDirectory(parent) || !Files.isWritable(parent)) throw new IllegalArgumentException("The selected backup folder is not writable.");
    }

    private Path uniqueSafetyBackupPath(Path directory) {
        String stem = "pre_restore_backup_" + FILE_TIME.format(LocalDateTime.now());
        Path candidate = directory.resolve(stem + ".sql");
        int sequence = 1;
        while (Files.exists(candidate)) {
            candidate = directory.resolve(stem + "_" + sequence++ + ".sql");
        }
        return candidate;
    }
    private String option(String value) { return '"' + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "").replace("\n", "") + '"'; }
    private String safeDiagnostic(Path errorFile, String password) {
        try {
            String text = Files.readString(errorFile, StandardCharsets.UTF_8);
            if (password != null && !password.isEmpty()) text = text.replace(password, "[redacted]");
            text = text.replaceAll("(?i)password\\s*=\\s*[^\\s]+", "password=[redacted]").trim();
            return text.isBlank() ? "Check MySQL tool installation and database access." : text.substring(0, Math.min(500, text.length()));
        } catch (IOException ignored) { return "Check MySQL tool installation and database access."; }
    }
    private void deleteQuietly(Path file) { try { Files.deleteIfExists(file); } catch (IOException ignored) { } }

    public record ToolPaths(Path mysqldump, Path mysql, String database) { }
    public record BackupResult(Path path, LocalDateTime completedAt, long bytes) { }
    public record RestoreResult(Path restoredFrom, Path safetyBackup) { }
    private record ConnectionTarget(String host, int port, String database) { }
}
