package uz.tabriko.infrastructure.payment;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Component
@Primary
@ConditionalOnProperty(name = "app.integrations.live", havingValue = "true")
public class PaymentProviderRouter implements PaymentProvider {

    private final ClickPaymentProvider click;
    private final PaymePaymentProvider payme;

    public PaymentProviderRouter(ClickPaymentProvider click, PaymePaymentProvider payme) {
        this.click = click;
        this.payme = payme;
    }

    @Override
    public PaymentInitResult initTopUp(UUID userId, BigDecimal amount, Long walletTxId, PaymentProviderType providerType) {
        return switch (providerType) {
            case CLICK -> click.initTopUp(userId, amount, walletTxId, providerType);
            case PAYME -> payme.initTopUp(userId, amount, walletTxId, providerType);
        };
    }
}
