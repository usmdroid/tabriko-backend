package uz.tabriko.dto.response;

import lombok.Data;
import uz.tabriko.domain.enums.MessageAuthor;

import java.time.Instant;

@Data
public class OrderMessageResponse {
    private Long id;
    private MessageAuthor author;
    private String text;
    private Instant createdAt;
}
