package uz.tabriko.infrastructure.payment;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Base64;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "app.integrations.live", havingValue = "true")
@Slf4j
public class PaymePaymentProvider implements PaymentProvider {

    private static final String CHECKOUT_BASE = "https://checkout.paycom.uz/";

    @Value("${PAYME_MERCHANT_ID}")
    private String merchantId;

    @Override
    public PaymentInitResult initTopUp(UUID userId, BigDecimal amount, Long walletTxId, PaymentProviderType providerType) {
        long amountInTiyin = amount.multiply(BigDecimal.valueOf(100)).longValueExact();
        String params = "m=" + merchantId + ";ac.account_id=" + walletTxId + ";a=" + amountInTiyin;
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(params.getBytes());
        String paymentUrl = CHECKOUT_BASE + encoded;
        String providerRef = "PAYME-" + walletTxId + "-" + UUID.randomUUID().toString().substring(0, 8);

        log.info("[PAYME] Topup initiated for user={} amount={} txId={}", userId, amount, walletTxId);
        return new PaymentInitResult(paymentUrl, providerRef, amount);
    }
}
