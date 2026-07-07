package uz.tabriko.dto.response;

import lombok.Data;

import java.util.UUID;

@Data
public class ApplicationSubmitResponse {
    private UUID applicationId;
    private String trackingToken;
    private String status;
    private String igVerifyCode;
    private String igInstructions;
}
