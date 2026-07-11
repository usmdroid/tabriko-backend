package uz.tabriko.dto.response;

import lombok.Data;
import uz.tabriko.domain.enums.OrderType;

import java.math.BigDecimal;
import java.time.Instant;

@Data
public class CreatorServiceResponse {
    private OrderType type;
    private BigDecimal price;
    private BigDecimal effectivePrice;
    private boolean onSale;
    private Integer discountPercent;
    private Instant discountEndsAt;
    private int deliveryDays;
    private boolean accepting;
}
