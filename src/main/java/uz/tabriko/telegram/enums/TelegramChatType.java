package uz.tabriko.telegram.enums;

// Telegram Bot API chat "type" values reachable via bot admin promotion (my_chat_member).
// Constant names intentionally match the raw API strings (see Chat.getType()) so no
// migration is needed to normalize pre-existing data.
public enum TelegramChatType {
    channel,
    group,
    supergroup
}
