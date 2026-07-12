package uz.tabriko.infrastructure.payment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ClickPaymentProviderTest {

    private static final String API_URL = "https://my.click.uz/services/pay";

    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private ClickPaymentProvider provider;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        provider = new ClickPaymentProvider(restTemplate);
        ReflectionTestUtils.setField(provider, "serviceId", "1234");
        ReflectionTestUtils.setField(provider, "merchantId", "5678");
        ReflectionTestUtils.setField(provider, "secretKey", "secret");
        ReflectionTestUtils.setField(provider, "apiUrl", API_URL);
    }

    @Test
    void initTopUp_postsToClickApi_andReturnsPaymentUrl() {
        String responseBody = "{\"payment_url\":\"https://my.click.uz/pay/999\",\"click_trans_id\":\"TX-001\"}";
        server.expect(requestTo(API_URL))
              .andExpect(method(HttpMethod.POST))
              .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        UUID userId = UUID.randomUUID();
        PaymentInitResult result = provider.initTopUp(userId, new BigDecimal("50.00"), 42L, PaymentProviderType.CLICK);

        server.verify();
        assertThat(result.getPaymentUrl()).isEqualTo("https://my.click.uz/pay/999");
        assertThat(result.getProviderTransactionId()).isEqualTo("TX-001");
        assertThat(result.getAmount()).isEqualByComparingTo("50.00");
    }

    @Test
    void initTopUp_missingPaymentUrl_returnsEmptyString() {
        server.expect(requestTo(API_URL))
              .andExpect(method(HttpMethod.POST))
              .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        PaymentInitResult result = provider.initTopUp(UUID.randomUUID(), new BigDecimal("10.00"), 1L, PaymentProviderType.CLICK);

        server.verify();
        assertThat(result.getPaymentUrl()).isEmpty();
    }
}
