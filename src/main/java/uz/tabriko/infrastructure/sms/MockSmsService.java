package uz.tabriko.infrastructure.sms;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.integrations.live", havingValue = "false", matchIfMissing = true)
@Slf4j
public class MockSmsService implements SmsService {

    @Override
    public void send(String phone, String message) {
        log.info("[SMS MOCK] To: {} | {}", phone, message);
    }
}
