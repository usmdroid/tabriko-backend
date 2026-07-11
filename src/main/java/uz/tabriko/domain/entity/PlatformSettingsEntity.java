package uz.tabriko.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "platform_settings")
@Getter
@Setter
public class PlatformSettingsEntity {

    @Id
    @Column(name = "id")
    private Integer id = 1;

    @Column(name = "orders_open", nullable = false)
    private boolean ordersOpen = true;

    @Column(name = "maintenance_mode", nullable = false)
    private boolean maintenanceMode = false;

    @Column(name = "registration_open", nullable = false)
    private boolean registrationOpen = true;

    @Column(name = "block_rooted_devices", nullable = false)
    private boolean blockRootedDevices = false;
}
