package uz.tabriko.telegram.enums;

// Telegram Bot API chat member "status" values recognized as an owner/admin.
// Constant names intentionally match the raw API strings (see ChatMember.getStatus()) so no
// migration is needed to normalize pre-existing data.
public enum TelegramOwnerStatus {
    creator,
    administrator
}
