package uz.tabriko.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import uz.tabriko.infrastructure.payment.PaymentProviderType;

import java.math.BigDecimal;

@Data
public class TopUpRequest {

    @NotNull @Positive
    private BigDecimal amount;

    @NotNull
    private PaymentProviderType provider;
}
