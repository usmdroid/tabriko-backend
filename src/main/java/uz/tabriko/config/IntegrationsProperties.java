package uz.tabriko.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.integrations")
@Getter
@Setter
public class IntegrationsProperties {
    private boolean live = false;
}
