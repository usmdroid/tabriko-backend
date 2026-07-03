package uz.tabriko.infrastructure.payment;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PaymentResult {
    private boolean success;
    private String transactionId;
    private String message;

    public static PaymentResult ok(String txId) {
        return new PaymentResult(true, txId, "OK");
    }

    public static PaymentResult fail(String reason) {
        return new PaymentResult(false, null, reason);
    }
}
