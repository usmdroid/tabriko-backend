package uz.tabriko.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class WalletCallbackRequest {

    // Our internal wallet_transactions.id issued during topup
    @NotNull
    private Long transactionId;

    @NotNull @Positive
    private BigDecimal amount;

    // Provider's own reference ID
    private String providerRef;

    // TODO: add HMAC signature fields for Click/Payme signature verification
}
