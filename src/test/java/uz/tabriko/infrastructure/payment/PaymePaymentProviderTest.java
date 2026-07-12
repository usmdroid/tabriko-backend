package uz.tabriko.infrastructure.payment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PaymePaymentProviderTest {

    private PaymePaymentProvider provider;

    @BeforeEach
    void setUp() {
        provider = new PaymePaymentProvider();
        ReflectionTestUtils.setField(provider, "merchantId", "TEST_MERCHANT");
    }

    @Test
    void initTopUp_buildsBase64CheckoutUrl() {
        UUID userId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("100.00");
        Long walletTxId = 7L;

        PaymentInitResult result = provider.initTopUp(userId, amount, walletTxId, PaymentProviderType.PAYME);

        assertThat(result.getPaymentUrl()).startsWith("https://checkout.paycom.uz/");
        String encoded = result.getPaymentUrl().replace("https://checkout.paycom.uz/", "");
        String decoded = new String(Base64.getUrlDecoder().decode(encoded));
        assertThat(decoded).isEqualTo("m=TEST_MERCHANT;ac.account_id=7;a=10000");
    }

    @Test
    void initTopUp_amountConvertedToTiyin() {
        PaymentInitResult result = provider.initTopUp(UUID.randomUUID(), new BigDecimal("25.50"), 1L, PaymentProviderType.PAYME);

        String encoded = result.getPaymentUrl().replace("https://checkout.paycom.uz/", "");
        String decoded = new String(Base64.getUrlDecoder().decode(encoded));
        assertThat(decoded).contains("a=2550");
    }

    @Test
    void initTopUp_amountAndProviderSetCorrectly() {
        PaymentInitResult result = provider.initTopUp(UUID.randomUUID(), new BigDecimal("50.00"), 3L, PaymentProviderType.PAYME);

        assertThat(result.getAmount()).isEqualByComparingTo("50.00");
        assertThat(result.getProviderTransactionId()).isNotBlank();
    }
}
