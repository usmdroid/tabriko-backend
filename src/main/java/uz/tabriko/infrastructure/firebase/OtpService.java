package uz.tabriko.infrastructure.firebase;

public interface OtpService {
    void sendOtp(String phone);
    boolean verifyOtp(String phone, String code);
}
