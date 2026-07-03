package uz.tabriko.infrastructure.payment;

import java.math.BigDecimal;
import java.util.UUID;

public interface PaymentGateway {
    PaymentResult hold(UUID userId, BigDecimal amount, UUID orderId);
    PaymentResult release(UUID toUserId, BigDecimal amount, UUID orderId);
    PaymentResult refund(UUID userId, BigDecimal amount, UUID orderId);
}
