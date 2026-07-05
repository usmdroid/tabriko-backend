package uz.tabriko.telegram.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.DefaultAbsSender;
import org.telegram.telegrambots.bots.DefaultBotOptions;

/**
 * Thin wrapper around DefaultAbsSender that provides execute() for Telegram API calls
 * without starting a long-polling or webhook bot session — Spring handles the webhook endpoint.
 */
@Component
public class TelegramBotClient extends DefaultAbsSender {

    public TelegramBotClient(@Value("${app.telegram.bot-token}") String botToken) {
        super(new DefaultBotOptions(), botToken);
    }
}
