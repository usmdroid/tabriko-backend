package uz.tabriko.dto.request;

import lombok.Data;

@Data
public class UpdateCreatorKycRequest {
    private String passportNumber;
    private String passportFileUrl;
    private String paymentCardNumber;
    private String paymentHolderName;
    private String telegram;
    private String instagram;
}
