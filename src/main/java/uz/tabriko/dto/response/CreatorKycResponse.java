package uz.tabriko.dto.response;

import lombok.Data;

@Data
public class CreatorKycResponse {
    private String passportNumber;    // masked: ****XXXX
    private String passportFileUrl;
    private String paymentCardNumber; // masked: **** **** **** XXXX
    private String paymentHolderName;
    private String telegram;
    private String instagram;
}
