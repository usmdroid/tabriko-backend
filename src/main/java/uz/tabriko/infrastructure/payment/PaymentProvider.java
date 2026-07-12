package uz.tabriko.infrastructure.payment;

import java.math.BigDecimal;
import java.util.UUID;

public interface PaymentProvider {
    // Initiate a topup payment; returns payment URL and provider transaction ID
    PaymentInitResult initTopUp(UUID userId, BigDecimal amount, Long walletTxId, PaymentProviderType providerType);
}
