package uz.tabriko.dto.response;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class TopUpInitResponse {
    private Long walletTransactionId;
    private String paymentUrl;
    private String providerTransactionId;
    private BigDecimal amount;
    private String provider;
}
