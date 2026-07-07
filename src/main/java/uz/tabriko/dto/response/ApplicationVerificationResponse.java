package uz.tabriko.dto.response;

import lombok.Data;

@Data
public class ApplicationVerificationResponse {

    private TelegramDetail telegram;
    private InstagramDetail instagram;

    @Data
    public static class TelegramDetail {
        private boolean verified;
        private String channelName;
        private String channelUsername;
        private Integer subscribers;
        private String ownerStatus;
        private String chatType;
    }

    @Data
    public static class InstagramDetail {
        private String username;
        private String verifyCode;
        private boolean ownershipConfirmed;
    }
}
