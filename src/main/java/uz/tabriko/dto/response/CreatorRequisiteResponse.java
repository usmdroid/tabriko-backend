package uz.tabriko.dto.response;

import lombok.Data;
import uz.tabriko.domain.enums.RequisiteSource;

@Data
public class CreatorRequisiteResponse {
    private Long id;
    private String name;
    private String emoji;
    private RequisiteSource source;
}
