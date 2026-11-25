package com.mycompany.easymartpos.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PasswordHasherTest {
    @Test void hashesWithSaltAndRejectsIncorrectPassword() {
        char[] password = "a long unique password".toCharArray();
        String first = PasswordHasher.hash(password);
        String second = PasswordHasher.hash(password);
        assertNotEquals(first, second);
        assertTrue(PasswordHasher.verify(password, first));
        assertFalse(PasswordHasher.verify("incorrect password".toCharArray(), first));
        assertFalse(PasswordHasher.verify(password, "pbkdf2-sha256$broken"));
    }

    @Test void acceptsLegacyPlaintextForOneTimeUpgradeAndRequiresStrongNewPasswords() {
        assertTrue(PasswordHasher.verify("old-short".toCharArray(), "old-short"));
        assertTrue(PasswordHasher.verify("old-short".toCharArray(), PasswordHasher.hashLegacy("old-short".toCharArray())));
        assertThrows(IllegalArgumentException.class, () -> PasswordHasher.hash("short".toCharArray()));
    }
}
