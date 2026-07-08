package uz.tabriko.dto.response;

import lombok.Data;

import java.util.UUID;

@Data
public class PhoneVerifyResponse {
    private String phone;
    private String verifyToken;
    private String igVerifyCode;

    // If this phone already has an active application, its id + tracking token are
    // returned so the client can send the applicant straight to the status page.
    private UUID existingApplicationId;
    private String existingTrackingToken;
}
