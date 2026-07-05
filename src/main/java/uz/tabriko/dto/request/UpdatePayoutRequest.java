package uz.tabriko.dto.request;

import lombok.Data;

@Data
public class UpdatePayoutRequest {
    private String card;
    private String account;
    private String holder;
}
