package uz.tabriko.dto.response;

import lombok.Data;

@Data
public class PlatformSettings {
    private Boolean ordersOpen;
    private Boolean maintenanceMode;
    private Boolean registrationOpen;
}
