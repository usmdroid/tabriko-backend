package uz.tabriko.dto.response;

import lombok.Data;

@Data
public class PhoneVerifyResponse {
    private String phone;
    private String verifyToken;
}
