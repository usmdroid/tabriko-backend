package uz.tabriko.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DeleteAccountRequest {

    // Why the account is being deleted — required and recorded in the audit.
    @NotBlank
    private String reason;
}
