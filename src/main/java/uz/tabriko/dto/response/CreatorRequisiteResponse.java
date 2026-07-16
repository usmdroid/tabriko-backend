package uz.tabriko.dto.response;

import lombok.Data;
import uz.tabriko.domain.enums.OrderType;
import uz.tabriko.domain.enums.RequisiteSource;

import java.math.BigDecimal;

@Data
public class CreatorRequisiteResponse {
    private Long id;
    private String name;
    private String emoji;
    private RequisiteSource source;
    private OrderType serviceType;
    private BigDecimal price;
}
