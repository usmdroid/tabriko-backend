package uz.tabriko.common.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessageDto {
    private int code;
    private String text;

    public static MessageDto ok() {
        return new MessageDto(0, "OK");
    }

    public static MessageDto of(int code, String text) {
        return new MessageDto(code, text);
    }
}
