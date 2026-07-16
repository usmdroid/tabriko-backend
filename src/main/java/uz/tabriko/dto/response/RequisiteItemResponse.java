package uz.tabriko.dto.response;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class RequisiteItemResponse {
    private Long id;
    private String name;
    private String emoji;
    private BigDecimal price;
}
