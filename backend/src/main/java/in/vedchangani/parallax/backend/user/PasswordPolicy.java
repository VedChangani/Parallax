package in.vedchangani.parallax.backend.user;

import java.nio.charset.StandardCharsets;

public final class PasswordPolicy {

    private static final int MIN_LENGTH = 8;
    private static final int MAX_UTF8_BYTES = 72;

    private PasswordPolicy() {
    }

    public static String violation(String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            return "password must be at least " + MIN_LENGTH + " characters";
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_UTF8_BYTES) {
            return "password must be at most " + MAX_UTF8_BYTES + " bytes";
        }
        return null;
    }

    public static boolean isValid(String password) {
        return violation(password) == null;
    }
}
