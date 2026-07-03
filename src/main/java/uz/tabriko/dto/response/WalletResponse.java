package uz.tabriko.dto.response;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class WalletResponse {
    private BigDecimal balance;
    private BigDecimal hold;
    private PageResponse<WalletTransactionResponse> history;
}
