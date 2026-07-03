package uz.tabriko.security;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

@Getter
@RequiredArgsConstructor
public class UserPrincipal {
    private final UUID userId;
    private final String phone;
    private final String role;
}
