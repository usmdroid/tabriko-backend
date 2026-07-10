package uz.tabriko.dto.request;

import jakarta.validation.constraints.NotBlank;
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

    // HMAC-SHA256 hex signature over "transactionId:amount:providerRef", computed with the
    // shared app.payment.callback-secret; verified in WalletService.handleCallback.
    @NotBlank
    private String signature;
}
