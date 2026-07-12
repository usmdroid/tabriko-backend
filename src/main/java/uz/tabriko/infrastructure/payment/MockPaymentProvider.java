package uz.tabriko.infrastructure.payment;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "app.integrations.live", havingValue = "false", matchIfMissing = true)
@Slf4j
public class MockPaymentProvider implements PaymentProvider {

    @Override
    public PaymentInitResult initTopUp(UUID userId, BigDecimal amount, Long walletTxId, PaymentProviderType providerType) {
        String fakeRef = "MOCK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String fakeUrl = "https://pay.mock.test/pay?txId=" + walletTxId + "&ref=" + fakeRef;
        log.info("[PAY-MOCK] Topup initiated for user={} amount={} txId={}", userId, amount, walletTxId);
        return new PaymentInitResult(fakeUrl, fakeRef, amount);
    }
}
