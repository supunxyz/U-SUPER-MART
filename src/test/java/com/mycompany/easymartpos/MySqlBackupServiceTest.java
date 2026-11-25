package com.mycompany.easymartpos;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import com.mycompany.easymartpos.service.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class MySqlBackupServiceTest {

    @AfterEach
    void clearSession() {
        Session.clear();
    }

    @Test
    void backupOperationRequiresAnAdminSessionBeforeAnyToolRuns() {
        assertThrows(SecurityException.class,
                () -> new MySqlBackupService().backup(Path.of("target", "not-created.sql")));
    }

    @Test
    void acceptsANonEmptyReadableSqlFile() throws Exception {
        Path backup = Files.createTempFile("easymart-backup-test-", ".sql");
        try {
            Files.writeString(backup, "-- harmless test backup\n");
            assertDoesNotThrow(() -> MySqlBackupService.validateRestoreFile(backup));
        } finally {
            Files.deleteIfExists(backup);
        }
    }

    @Test
    void rejectsMissingOrEmptyRestoreFiles() throws Exception {
        Path missing = Path.of("target", "missing-easymart-backup-test.sql");
        assertThrows(IllegalArgumentException.class, () -> MySqlBackupService.validateRestoreFile(missing));

        Path empty = Files.createTempFile("easymart-empty-backup-test-", ".sql");
        try {
            assertThrows(IllegalArgumentException.class, () -> MySqlBackupService.validateRestoreFile(empty));
        } finally {
            Files.deleteIfExists(empty);
        }
    }
}
