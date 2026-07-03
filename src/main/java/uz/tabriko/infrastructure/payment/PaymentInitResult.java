package uz.tabriko.infrastructure.payment;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class PaymentInitResult {
    private String paymentUrl;
    private String providerTransactionId;
    private BigDecimal amount;
}
