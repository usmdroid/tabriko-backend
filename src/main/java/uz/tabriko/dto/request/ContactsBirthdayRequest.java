package uz.tabriko.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class ContactsBirthdayRequest {

    @NotNull
    @Size(max = 2000, message = "Too many hashes; maximum is 2000")
    private List<String> hashes;
}
