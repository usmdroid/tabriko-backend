package uz.tabriko.infrastructure.firebase;

import java.util.Map;

public interface PushNotificationService {
    void sendPush(String fcmToken, String title, String body, Map<String, String> data);

    class DeadTokenException extends RuntimeException {
        public DeadTokenException(String fcmToken) { super(fcmToken); }
    }
}
