package uz.tabriko.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AddCreatorRequisiteRequest {

    private Long catalogId;

    @Size(max = 60)
    private String customName;
}
