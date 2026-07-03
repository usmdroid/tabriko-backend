package uz.tabriko.infrastructure.payment;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

// Stub payment gateway — always succeeds; replace with Click/Payme in production
@Service
@Slf4j
public class MockPaymentGateway implements PaymentGateway {

    @Override
    public PaymentResult hold(UUID userId, BigDecimal amount, UUID orderId) {
        String txId = "HOLD-" + UUID.randomUUID();
        log.info("[PAYMENT] HOLD userId={} amount={} orderId={} txId={}", userId, amount, orderId, txId);
        return PaymentResult.ok(txId);
    }

    @Override
    public PaymentResult release(UUID toUserId, BigDecimal amount, UUID orderId) {
        String txId = "RELEASE-" + UUID.randomUUID();
        log.info("[PAYMENT] RELEASE userId={} amount={} orderId={} txId={}", toUserId, amount, orderId, txId);
        return PaymentResult.ok(txId);
    }

    @Override
    public PaymentResult refund(UUID userId, BigDecimal amount, UUID orderId) {
        String txId = "REFUND-" + UUID.randomUUID();
        log.info("[PAYMENT] REFUND userId={} amount={} orderId={} txId={}", userId, amount, orderId, txId);
        return PaymentResult.ok(txId);
    }
}
