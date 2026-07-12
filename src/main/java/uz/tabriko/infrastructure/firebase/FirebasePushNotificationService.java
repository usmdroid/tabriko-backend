package uz.tabriko.infrastructure.firebase;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Map;

@Service
@ConditionalOnExpression("!'${app.firebase.credentials-path:}'.isEmpty()")
@Slf4j
public class FirebasePushNotificationService implements PushNotificationService {

    @Value("${app.firebase.credentials-path}")
    private String credentialsPath;

    @PostConstruct
    void init() {
        if (FirebaseApp.getApps().isEmpty()) {
            try {
                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(new FileInputStream(credentialsPath)))
                        .build();
                FirebaseApp.initializeApp(options);
            } catch (IOException e) {
                throw new RuntimeException("Failed to initialize Firebase from: " + credentialsPath, e);
            }
        }
    }

    @Override
    public void sendPush(String fcmToken, String title, String body, Map<String, String> data) {
        if (fcmToken == null || fcmToken.isBlank()) {
            // A device row without a usable token (registration never completed).
            // Treat as a dead token so the caller can prune it; never let a null
            // token reach Message.builder(), which would throw and fail the batch.
            throw new PushNotificationService.DeadTokenException(fcmToken == null ? "" : fcmToken);
        }
        try {
            Message message = Message.builder()
                    .setToken(fcmToken)
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build())
                    .putAllData(data == null ? Map.of() : data)
                    .build();
            FirebaseMessaging.getInstance().send(message);
        } catch (FirebaseMessagingException e) {
            String code = e.getMessagingErrorCode() != null ? e.getMessagingErrorCode().name() : "";
            if ("UNREGISTERED".equals(code) || "INVALID_ARGUMENT".equals(code)) {
                throw new PushNotificationService.DeadTokenException(fcmToken);
            }
            log.warn("[PUSH] Failed to send to token={}: {}", fcmToken, e.getMessage());
        }
    }
}
