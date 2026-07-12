package uz.tabriko.infrastructure.payment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentProviderRouterTest {

    @Mock ClickPaymentProvider click;
    @Mock PaymePaymentProvider payme;

    @Test
    void router_click_delegatesToClickProvider() {
        PaymentProviderRouter router = new PaymentProviderRouter(click, payme);
        UUID userId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("100.00");
        PaymentInitResult expected = new PaymentInitResult("https://click.uz/pay", "TX-1", amount);
        when(click.initTopUp(userId, amount, 42L, PaymentProviderType.CLICK)).thenReturn(expected);

        PaymentInitResult result = router.initTopUp(userId, amount, 42L, PaymentProviderType.CLICK);

        assertThat(result).isSameAs(expected);
        verify(click).initTopUp(userId, amount, 42L, PaymentProviderType.CLICK);
        verify(payme, never()).initTopUp(any(), any(), any(), any());
    }

    @Test
    void router_payme_delegatesToPaymeProvider() {
        PaymentProviderRouter router = new PaymentProviderRouter(click, payme);
        UUID userId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("50.00");
        PaymentInitResult expected = new PaymentInitResult("https://checkout.paycom.uz/xxx", "PAYME-7-abc", amount);
        when(payme.initTopUp(userId, amount, 7L, PaymentProviderType.PAYME)).thenReturn(expected);

        PaymentInitResult result = router.initTopUp(userId, amount, 7L, PaymentProviderType.PAYME);

        assertThat(result).isSameAs(expected);
        verify(payme).initTopUp(userId, amount, 7L, PaymentProviderType.PAYME);
        verify(click, never()).initTopUp(any(), any(), any(), any());
    }

    @Test
    void liveOn_routerIsRegisteredAsPrimary() {
        new ApplicationContextRunner()
            .withUserConfiguration(ClickPaymentProvider.class, PaymePaymentProvider.class, PaymentProviderRouter.class)
            .withPropertyValues(
                "app.integrations.live=true",
                "CLICK_SERVICE_ID=1234",
                "CLICK_MERCHANT_ID=5678",
                "CLICK_SECRET_KEY=secret",
                "PAYME_MERCHANT_ID=m123"
            )
            .withBean(org.springframework.web.client.RestTemplate.class)
            .run(ctx -> {
                // primary bean resolves to the router even though Click and Payme are also registered
                assertThat(ctx.getBean(PaymentProvider.class)).isInstanceOf(PaymentProviderRouter.class);
            });
    }
}
