package uz.tabriko.telegram.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.telegram.telegrambots.meta.api.objects.Update;
import uz.tabriko.telegram.service.TelegramBotService;

@RestController
@RequestMapping("/api/v1/telegram")
@RequiredArgsConstructor
public class TelegramWebhookController {

    private final TelegramBotService botService;

    @Value("${app.telegram.webhook-secret}")
    private String webhookSecret;

    @PostMapping("/webhook")
    public ResponseEntity<Void> handleWebhook(
        @RequestHeader(value = "X-Telegram-Bot-Api-Secret-Token", required = false) String secretToken,
        @RequestBody Update update
    ) {
        if (webhookSecret == null || webhookSecret.isBlank() || !webhookSecret.equals(secretToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        botService.handleUpdate(update);
        return ResponseEntity.ok().build();
    }
}
