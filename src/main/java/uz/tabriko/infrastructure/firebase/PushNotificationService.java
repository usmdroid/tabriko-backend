package uz.tabriko.infrastructure.firebase;

public interface PushNotificationService {
    void sendPush(String fcmToken, String title, String body);
}
