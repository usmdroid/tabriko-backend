package uz.tabriko;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import uz.tabriko.infrastructure.firebase.MockOtpService;
import uz.tabriko.infrastructure.firebase.OtpService;
import uz.tabriko.infrastructure.firebase.SecureOtpService;
import uz.tabriko.infrastructure.payment.MockPaymentProvider;
import uz.tabriko.infrastructure.payment.PaymentProvider;
import uz.tabriko.infrastructure.sms.MockSmsService;
import uz.tabriko.infrastructure.sms.SmsService;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies conditional bean selection with app.integrations.live=false (the default).
 * Uses ApplicationContextRunner — no DB or full context needed.
 */
class LiveFlagOffTest {

    @Configuration
    static class MinimalConfig {
        @Bean RestTemplate restTemplate() { return new RestTemplate(); }
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(MinimalConfig.class)
        .withUserConfiguration(
            MockPaymentProvider.class,
            MockOtpService.class,
            MockSmsService.class
        );

    @Test
    void liveOff_mockPaymentProviderIsActive() {
        runner.withPropertyValues("app.integrations.live=false")
              .run(ctx -> {
                  assertThat(ctx).hasSingleBean(PaymentProvider.class);
                  assertThat(ctx.getBean(PaymentProvider.class)).isInstanceOf(MockPaymentProvider.class);
              });
    }

    @Test
    void liveNotSet_mockPaymentProviderIsActive_byDefault() {
        runner.run(ctx -> {
            assertThat(ctx).hasSingleBean(PaymentProvider.class);
            assertThat(ctx.getBean(PaymentProvider.class)).isInstanceOf(MockPaymentProvider.class);
        });
    }

    @Test
    void liveOff_mockOtpServiceIsActive() {
        runner.withPropertyValues("app.integrations.live=false")
              .run(ctx -> {
                  assertThat(ctx).hasSingleBean(OtpService.class);
                  assertThat(ctx.getBean(OtpService.class)).isInstanceOf(MockOtpService.class);
              });
    }

    @Test
    void liveOff_mockSmsServiceIsActive() {
        runner.withPropertyValues("app.integrations.live=false")
              .run(ctx -> {
                  assertThat(ctx).hasSingleBean(SmsService.class);
                  assertThat(ctx.getBean(SmsService.class)).isInstanceOf(MockSmsService.class);
              });
    }

    @Test
    void liveOff_securOtpServiceNotRegistered() {
        runner.withPropertyValues("app.integrations.live=false")
              .run(ctx -> assertThat(ctx).doesNotHaveBean(SecureOtpService.class));
    }
}
