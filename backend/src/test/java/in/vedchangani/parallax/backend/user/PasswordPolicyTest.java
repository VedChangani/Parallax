package in.vedchangani.parallax.backend.user;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordPolicyTest {

    @Test
    void sevenCharactersIsRejectedWithTheMinimumInTheMessage() {
        assertEquals("password must be at least 8 characters", PasswordPolicy.violation("x".repeat(7)));
        assertFalse(PasswordPolicy.isValid("x".repeat(7)));
    }

    @Test
    void eightCharactersIsAccepted() {
        assertNull(PasswordPolicy.violation("x".repeat(8)));
        assertTrue(PasswordPolicy.isValid("x".repeat(8)));
    }

    @Test
    void nullAndEmptyAreRejected() {
        assertEquals("password must be at least 8 characters", PasswordPolicy.violation(null));
        assertEquals("password must be at least 8 characters", PasswordPolicy.violation(""));
    }

    @Test
    void theSeventyTwoByteMaximumIsUnchanged() {
        assertNull(PasswordPolicy.violation("x".repeat(72)));
        assertEquals("password must be at most 72 bytes", PasswordPolicy.violation("x".repeat(73)));
        // 8 characters but 96 UTF-8 bytes (4 bytes each): still rejected by the byte limit.
        assertEquals("password must be at most 72 bytes", PasswordPolicy.violation("\uD83D\uDE00".repeat(24)));
    }

    @Test
    void noCompositionRulesApply() {
        assertTrue(PasswordPolicy.isValid("aaaaaaaa"));
        assertTrue(PasswordPolicy.isValid("12345678"));
        assertTrue(PasswordPolicy.isValid("        "));
    }

    @Test
    void theViolationMessageNeverContainsThePassword() {
        assertFalse(PasswordPolicy.violation("secret7").contains("secret7"));
    }
}
