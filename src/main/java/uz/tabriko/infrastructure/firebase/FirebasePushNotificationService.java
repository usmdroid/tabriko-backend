package uz.tabriko.infrastructure.firebase;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.Aps;
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

    // Must match the channel the mobile app creates at startup and the
    // `default_notification_channel_id` in its AndroidManifest. The channel is
    // registered with IMPORTANCE_HIGH on the device so pushes make a sound and
    // pop up as a heads-up banner instead of arriving silently in the shade.
    private static final String ANDROID_CHANNEL_ID = "tabriko_default";

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
                    // Android: high priority + a high-importance channel + default
                    // sound so the notification is delivered promptly, makes a
                    // sound and shows as a heads-up banner.
                    .setAndroidConfig(AndroidConfig.builder()
                            .setPriority(AndroidConfig.Priority.HIGH)
                            .setNotification(AndroidNotification.builder()
                                    .setChannelId(ANDROID_CHANNEL_ID)
                                    .setSound("default")
                                    .setDefaultSound(true)
                                    .setDefaultVibrateTimings(true)
                                    .build())
                            .build())
                    // iOS: play the default sound (otherwise the banner is silent).
                    .setApnsConfig(ApnsConfig.builder()
                            .setAps(Aps.builder()
                                    .setSound("default")
                                    .build())
                            .build())
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
