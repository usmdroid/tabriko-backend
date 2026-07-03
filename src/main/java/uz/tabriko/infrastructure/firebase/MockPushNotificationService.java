package uz.tabriko.infrastructure.firebase;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

// Mock FCM push — logs only; swap for real Firebase impl in production
@Service
@Slf4j
public class MockPushNotificationService implements PushNotificationService {

    @Override
    public void sendPush(String fcmToken, String title, String body) {
        log.info("[PUSH] token={} title='{}' body='{}'", fcmToken, title, body);
    }
}
