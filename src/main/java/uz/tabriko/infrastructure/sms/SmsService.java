package uz.tabriko.infrastructure.sms;

public interface SmsService {
    void send(String phone, String message);
}
