package uz.tabriko.dto.response;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class EarningsResponse {
    private BigDecimal totalEarned;
    private BigDecimal pendingPayout;
    private BigDecimal withdrawn;
}
