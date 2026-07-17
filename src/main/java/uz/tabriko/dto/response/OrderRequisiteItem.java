package uz.tabriko.dto.response;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class OrderRequisiteItem {
    private String name;
    private String emoji;
    private BigDecimal price;
}
