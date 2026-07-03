package uz.tabriko.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
public class WalletTransactionResponse {
    private Long id;
    private BigDecimal amount;
    private String type;
    private String status;
    private UUID orderId;
    private Instant createdAt;
}
