package uz.tabriko.infrastructure.payment;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "app.integrations.live", havingValue = "true")
@Slf4j
public class ClickPaymentProvider implements PaymentProvider {

    private final RestTemplate restTemplate;

    @Value("${CLICK_SERVICE_ID}")
    private String serviceId;

    @Value("${CLICK_MERCHANT_ID}")
    private String merchantId;

    @Value("${CLICK_SECRET_KEY}")
    private String secretKey;

    @Value("${CLICK_API_URL:https://my.click.uz/services/pay}")
    private String apiUrl;

    public ClickPaymentProvider(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public PaymentInitResult initTopUp(UUID userId, BigDecimal amount, Long walletTxId, PaymentProviderType providerType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBasicAuth(merchantId, secretKey);

        Map<String, Object> body = new HashMap<>();
        body.put("service_id", serviceId);
        body.put("merchant_id", merchantId);
        body.put("amount", amount);
        body.put("merchant_trans_id", String.valueOf(walletTxId));
        body.put("return_url", "");

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        @SuppressWarnings("unchecked")
        Map<String, Object> response = restTemplate.postForObject(apiUrl, request, Map.class);

        String paymentUrl = response != null ? String.valueOf(response.getOrDefault("payment_url", "")) : "";
        String providerRef = response != null ? String.valueOf(response.getOrDefault("click_trans_id", UUID.randomUUID().toString())) : UUID.randomUUID().toString();

        log.info("[CLICK] Topup initiated for user={} amount={} txId={}", userId, amount, walletTxId);
        return new PaymentInitResult(paymentUrl, providerRef, amount);
    }
}
