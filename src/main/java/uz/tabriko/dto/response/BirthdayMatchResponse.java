package uz.tabriko.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.UUID;

@Data
@JsonInclude(JsonInclude.Include.ALWAYS)
public class BirthdayMatchResponse {
    private UUID userId;
    // Echo of the matched phone hash the client sent — lets the app map this
    // result back to the device contact (to show the local contact name).
    private String hash;
    private String name;
    private String avatarUrl;
    private int birthDay;
    private int birthMonth;
    private boolean isCreator;
    private String publicCode;
}
