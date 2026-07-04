package uz.tabriko.dto.response;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class AdminStatsResponse {
    private BigDecimal revenue;
    private long activeCreators;
    private long totalUsers;
    private long pendingOrders;
    private long totalOrders;
    private long moderationQueue;
}
