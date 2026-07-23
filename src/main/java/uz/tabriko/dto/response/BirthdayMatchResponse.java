package uz.tabriko.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.UUID;

@Data
@JsonInclude(JsonInclude.Include.ALWAYS)
public class BirthdayMatchResponse {
    private UUID userId;
    private String name;
    private String avatarUrl;
    private int birthDay;
    private int birthMonth;
    private boolean isCreator;
    private String publicCode;
}
