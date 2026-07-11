package uz.tabriko.security;

import org.springframework.stereotype.Component;

/**
 * Production login backdoor: always disabled. Overridden by
 * {@link DevLoginBackdoor} (@Primary) in the dev/test profiles.
 */
@Component
public class NoopLoginBackdoor implements LoginBackdoor {

    @Override
    public boolean matches(String rawPassword) {
        return false;
    }
}
