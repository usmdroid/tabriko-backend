package uz.tabriko.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SignedUrlResponse {
    private String url;
    private long expiresInSeconds;
}
