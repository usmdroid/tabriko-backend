package uz.tabriko.telegram.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChat;
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChatMember;
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChatMemberCount;
import org.telegram.telegrambots.meta.api.methods.groupadministration.LeaveChat;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.*;
import org.telegram.telegrambots.meta.api.objects.chatmember.ChatMember;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import uz.tabriko.domain.entity.ApplicationMessage;
import uz.tabriko.domain.enums.ApplicationStatus;
import uz.tabriko.domain.enums.MessageAuthor;
import uz.tabriko.repository.ApplicationMessageRepository;
import uz.tabriko.repository.CreatorApplicationRepository;
import uz.tabriko.telegram.entity.TelegramVerification;
import uz.tabriko.telegram.repository.TelegramVerificationRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramBotService {

    private final TelegramBotClient client;
    private final TelegramVerificationRepository repo;
    private final CreatorApplicationRepository applicationRepo;
    private final ApplicationMessageRepository messageRepo;

    @Transactional
    public void handleUpdate(Update update) {
        try {
            if (update.hasMessage()) {
                Message msg = update.getMessage();
                if (msg.hasText() && msg.getText().startsWith("/start")) {
                    handleStart(msg);
                } else if (msg.hasContact()) {
                    handleContact(msg);
                } else if (msg.hasText()) {
                    handleApplicantReply(msg);
                }
            } else if (update.hasMyChatMember()) {
                handleMyChatMember(update.getMyChatMember());
            } else if (update.hasCallbackQuery()) {
                handleCallbackQuery(update.getCallbackQuery());
            }
        } catch (Exception e) {
            log.error("Unexpected error processing update {}", update.getUpdateId(), e);
        }
    }

    private void handleStart(Message msg) {
        if (msg.getFrom() == null) {
            log.warn("Received /start message with null sender on chatId={}, ignoring", msg.getChatId());
            return;
        }
        Long telegramUserId = msg.getFrom().getId();
        Long chatId = msg.getChatId();

        TelegramVerification session = new TelegramVerification();
        session.setTelegramUserId(telegramUserId);
        session.setStatus("STARTED");
        repo.save(session);

        KeyboardButton contactBtn = new KeyboardButton("Raqamni ulashish");
        contactBtn.setRequestContact(true);
        KeyboardRow row = new KeyboardRow();
        row.add(contactBtn);

        ReplyKeyboardMarkup keyboard = new ReplyKeyboardMarkup();
        keyboard.setResizeKeyboard(true);
        keyboard.setOneTimeKeyboard(true);
        keyboard.setKeyboard(List.of(row));

        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId.toString());
        sendMessage.setText("Tekshiruvni boshladik");
        sendMessage.setReplyMarkup(keyboard);
        sendSafe(sendMessage);
    }

    private void handleContact(Message msg) {
        Long telegramUserId = msg.getFrom().getId();
        Long chatId = msg.getChatId();

        TelegramVerification session = repo.findFirstByTelegramUserIdOrderByCreatedAtDesc(telegramUserId)
            .orElse(null);
        if (session == null || !"STARTED".equals(session.getStatus())) {
            sendText(chatId, "Iltimos /start buyrug'ini yuboring.");
            return;
        }

        String phone = normalizePhone(msg.getContact().getPhoneNumber());
        session.setPhone(phone);

        // The applicant is not necessarily a registered User yet — match against a
        // submitted creator application by phone instead.
        boolean hasApplication = applicationRepo.findFirstByPhoneOrderByCreatedAtDesc(phone).isPresent();
        if (hasApplication) {
            session.setStatus("PHONE_LINKED");
        } else {
            session.setStatus("FAILED");
        }
        session.setUpdatedAt(Instant.now());
        repo.save(session);

        if ("PHONE_LINKED".equals(session.getStatus())) {
            sendText(chatId, "Raqamingiz tasdiqlandi");
            sendText(chatId,
                "Endi kanal yoki guruhingizni tasdiqlash uchun:\n" +
                "1. Kanalga/guruhga @tabrikoverifybot ni ENG KAM HUQUQLI admin qiling (barcha huquqlar O'CHIQ bo'lsin)\n" +
                "2. Bot avtomatik aniqlaydi va sizga xabar yuboradi"
            );
        } else {
            sendText(chatId, "Bu raqamga tegishli ariza topilmadi. Iltimos avval TabrikO saytida creator arizasini topshiring va qayta urinib ko'ring.");
        }
    }

    private void handleMyChatMember(ChatMemberUpdated chatMemberUpdated) {
        ChatMember newMember = chatMemberUpdated.getNewChatMember();
        if (newMember == null) return;

        String newStatus = newMember.getStatus();
        if (!"administrator".equals(newStatus) && !"creator".equals(newStatus)) {
            return;
        }

        org.telegram.telegrambots.meta.api.objects.User from = chatMemberUpdated.getFrom();
        Long telegramUserId = from.getId();
        Long chatId = chatMemberUpdated.getChat().getId();

        TelegramVerification session = repo.findFirstByTelegramUserIdOrderByCreatedAtDesc(telegramUserId)
            .orElse(null);
        if (session == null || !"PHONE_LINKED".equals(session.getStatus())) return;

        try {
            GetChatMember getChatMember = new GetChatMember();
            getChatMember.setChatId(chatId.toString());
            getChatMember.setUserId(telegramUserId);
            ChatMember member = client.execute(getChatMember);

            String ownerStatus = member.getStatus();
            if (!"creator".equals(ownerStatus) && !"administrator".equals(ownerStatus)) {
                sendText(telegramUserId, "Siz bu kanal/guruhda admin emassiz. Iltimos admin huquqlarini tekshiring va qayta urinib ko'ring.");
                return;
            }

            GetChatMemberCount getMemberCount = new GetChatMemberCount();
            getMemberCount.setChatId(chatId.toString());
            Integer count = client.execute(getMemberCount);

            GetChat getChat = new GetChat();
            getChat.setChatId(chatId.toString());
            Chat chatInfo = client.execute(getChat);

            session.setChatId(chatId);
            session.setChatUsername(chatInfo.getUserName());
            session.setChatTitle(chatInfo.getTitle());
            session.setChatType(chatMemberUpdated.getChat().getType());
            session.setSubscribers(count);
            session.setOwnerStatus(ownerStatus);
            session.setStatus("CHANNEL_READ");
            session.setUpdatedAt(Instant.now());
            repo.save(session);

            String title = chatInfo.getTitle() != null ? chatInfo.getTitle() : chatInfo.getUserName();
            String text = String.format("Aniqlandi: %s, %d obunachi, siz %s", title, count, ownerStatus);

            InlineKeyboardButton confirmBtn = new InlineKeyboardButton("Tasdiqlash / Yuborish");
            confirmBtn.setCallbackData("confirm_verification");
            List<List<InlineKeyboardButton>> inlineKb = new ArrayList<>();
            inlineKb.add(List.of(confirmBtn));
            InlineKeyboardMarkup inlineMarkup = new InlineKeyboardMarkup(inlineKb);

            SendMessage sendMessage = new SendMessage();
            sendMessage.setChatId(telegramUserId.toString());
            sendMessage.setText(text);
            sendMessage.setReplyMarkup(inlineMarkup);
            sendSafe(sendMessage);

        } catch (TelegramApiException e) {
            log.error("Error reading channel info for telegramUserId={}, chatId={}", telegramUserId, chatId, e);
            session.setStatus("FAILED");
            session.setUpdatedAt(Instant.now());
            repo.save(session);
            sendText(telegramUserId, "Kanaldan ma'lumot olishda xatolik yuz berdi. Iltimos qayta urinib ko'ring: /start");
        }
    }

    private void handleCallbackQuery(CallbackQuery callbackQuery) {
        if (!"confirm_verification".equals(callbackQuery.getData())) return;

        Long telegramUserId = callbackQuery.getFrom().getId();

        TelegramVerification session = repo.findFirstByTelegramUserIdOrderByCreatedAtDesc(telegramUserId)
            .orElse(null);
        if (session == null || !"CHANNEL_READ".equals(session.getStatus())) return;

        session.setStatus("VERIFIED");
        session.setVerifiedAt(Instant.now());
        session.setUpdatedAt(Instant.now());
        repo.save(session);

        // Link the completed verification to the applicant's submitted application,
        // so the status page and admin panel reflect it. Matched by phone.
        if (session.getPhone() != null) {
            applicationRepo.findFirstByPhoneOrderByCreatedAtDesc(session.getPhone())
                .ifPresent(app -> {
                    app.setTelegramVerification(session);
                    applicationRepo.save(app);
                });
        }

        try {
            AnswerCallbackQuery answer = new AnswerCallbackQuery();
            answer.setCallbackQueryId(callbackQuery.getId());
            client.execute(answer);
        } catch (TelegramApiException e) {
            log.warn("Failed to answer callback query {}", callbackQuery.getId(), e);
        }

        sendText(telegramUserId, "Tasdiqlandi! ✅ Kanal tekshiruvi yakunlandi.");

        // Best-effort cleanup: leave the channel. A failure here must NOT un-verify —
        // the verification is already saved above.
        if (session.getChatId() != null) {
            try {
                LeaveChat leaveChat = new LeaveChat();
                leaveChat.setChatId(session.getChatId().toString());
                client.execute(leaveChat);
                sendText(telegramUserId, "Kanaldan chiqdim. Rahmat!");
            } catch (TelegramApiException e) {
                log.warn("Failed to leave chat {} for telegramUserId={} (verification already saved)",
                    session.getChatId(), telegramUserId, e);
            }
        }
    }

    /**
     * A plain text message from an applicant. If the applicant's phone maps to an
     * application whose status is INFO_REQUESTED, store the text as their reply in the
     * application message thread (visible to the admin panel).
     */
    private void handleApplicantReply(Message msg) {
        if (msg.getFrom() == null) return;
        Long telegramUserId = msg.getFrom().getId();
        Long chatId = msg.getChatId();

        TelegramVerification session =
            repo.findFirstByTelegramUserIdOrderByCreatedAtDesc(telegramUserId).orElse(null);
        if (session == null || session.getPhone() == null) {
            sendText(chatId, "Avval /start bosib, telefon raqamingizni ulashing.");
            return;
        }

        var appOpt = applicationRepo.findFirstByPhoneOrderByCreatedAtDesc(session.getPhone());
        if (appOpt.isEmpty()) {
            sendText(chatId, "Bu raqamga tegishli ariza topilmadi.");
            return;
        }
        var app = appOpt.get();
        if (app.getStatus() != ApplicationStatus.INFO_REQUESTED) {
            sendText(chatId, "Hozircha javob talab qilinmagan. Rahmat!");
            return;
        }

        ApplicationMessage reply = new ApplicationMessage();
        reply.setApplication(app);
        reply.setAuthor(MessageAuthor.APPLICANT);
        reply.setText(msg.getText());
        reply.setCreatedAt(Instant.now());
        messageRepo.save(reply);

        sendText(chatId, "Javobingiz qabul qilindi. Rahmat!");
    }

    /**
     * Send an admin/moderator message to the applicant's Telegram (if they have verified
     * their phone via the bot). Best-effort — silently skips if no linked chat exists.
     */
    @Transactional(readOnly = true)
    public void notifyApplicant(String phone, String text) {
        if (phone == null || text == null) return;
        repo.findFirstByPhoneOrderByCreatedAtDesc(phone).ifPresent(v -> {
            if (v.getTelegramUserId() != null) {
                sendText(v.getTelegramUserId(), "TabrikO admin:\n" + text);
            }
        });
    }

    private String normalizePhone(String raw) {
        if (raw == null) return null;
        // Telegram sends phone numbers as digits only (no leading +)
        String digits = raw.replaceAll("[^0-9]", "");
        return "+" + digits;
    }

    private void sendText(Long chatId, String text) {
        SendMessage msg = new SendMessage();
        msg.setChatId(chatId.toString());
        msg.setText(text);
        sendSafe(msg);
    }

    private void sendSafe(SendMessage msg) {
        try {
            client.execute(msg);
        } catch (TelegramApiException e) {
            log.error("Failed to send message to chatId={}: {}", msg.getChatId(), e.getMessage());
        }
    }
}
