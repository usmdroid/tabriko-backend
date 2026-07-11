package uz.tabriko.security;

/**
 * Dev/test convenience: a master password that authenticates into any existing
 * (non-blocked) account, regardless of that account's stored password.
 *
 * The production implementation is a no-op ({@link NoopLoginBackdoor}); the
 * dev/test implementation ({@link DevLoginBackdoor}) accepts a fixed master
 * password and overrides it via {@code @Primary}. This mirrors the OTP backdoor
 * (MockOtpService / code 2580).
 */
public interface LoginBackdoor {

    /** @return true if {@code rawPassword} is the dev master password. */
    boolean matches(String rawPassword);
}
