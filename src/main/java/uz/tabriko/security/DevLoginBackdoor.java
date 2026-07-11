package uz.tabriko.security;

import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Dev/test login backdoor. Accepts a fixed master password for any account so
 * QA can sign in without knowing each account's real password.
 *
 * @Primary so it wins over {@link NoopLoginBackdoor} in these profiles.
 */
@Component
@Primary
@Profile({"dev", "test"})
public class DevLoginBackdoor implements LoginBackdoor {

    // FIXME: development backdoor — remove before production launch.
    private static final String MASTER_PASSWORD = "123456";

    @Override
    public boolean matches(String rawPassword) {
        return MASTER_PASSWORD.equals(rawPassword);
    }
}
