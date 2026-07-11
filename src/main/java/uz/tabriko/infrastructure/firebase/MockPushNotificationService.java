package uz.tabriko.infrastructure.firebase;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@ConditionalOnExpression("'${app.firebase.credentials-path:}'.isEmpty()")
@Slf4j
public class MockPushNotificationService implements PushNotificationService {

    @Override
    public void sendPush(String fcmToken, String title, String body, Map<String, String> data) {
        log.info("[PUSH] token={} title='{}' body='{}' data={}", fcmToken, title, body, data);
    }
}
