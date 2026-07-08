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
import uz.tabriko.telegram.entity.TelegramBotChat;
import uz.tabriko.telegram.entity.TelegramVerification;
import uz.tabriko.telegram.repository.TelegramBotChatRepository;
import uz.tabriko.telegram.repository.TelegramVerificationRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramBotService {

    private static final String CHECK_COMMAND = "/tekshirish";

    private final TelegramBotClient client;
    private final TelegramVerificationRepository repo;
    private final TelegramBotChatRepository botChatRepo;
    private final CreatorApplicationRepository applicationRepo;
    private final ApplicationMessageRepository messageRepo;

    @Transactional
    public void handleUpdate(Update update) {
        try {
            if (update.hasMessage()) {
                Message msg = update.getMessage();
                if (msg.hasText() && msg.getText().startsWith("/start")) {
                    handleStart(msg);
                } else if (msg.hasText() && msg.getText().trim().equalsIgnoreCase(CHECK_COMMAND)) {
                    handleCheckCommand(msg);
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
            reconcilePendingChats(session);
        } else {
            sendText(chatId, "Bu raqamga tegishli ariza topilmadi. Iltimos avval TabrikO saytida creator arizasini topshiring va qayta urinib ko'ring.");
        }
    }

    /**
     * Manual fallback: the creator returns to the bot after making it a channel/group
     * admin (whether that happened before or after /start) and asks it to re-check.
     */
    private void handleCheckCommand(Message msg) {
        if (msg.getFrom() == null) return;
        Long telegramUserId = msg.getFrom().getId();
        Long chatId = msg.getChatId();

        TelegramVerification session = repo.findFirstByTelegramUserIdOrderByCreatedAtDesc(telegramUserId)
            .orElse(null);
        if (session == null || session.getPhone() == null) {
            sendText(chatId, "Avval /start bosib, telefon raqamingizni ulashing.");
            return;
        }
        if ("VERIFIED".equals(session.getStatus())) {
            sendText(chatId, "Tekshiruv allaqachon yakunlangan. Rahmat!");
            return;
        }
        reconcilePendingChats(session);
    }

    /**
     * Looks up every chat this Telegram account has ever promoted the bot to admin in
     * (recorded regardless of when that happened relative to /start) and either
     * auto-confirms the single match, offers a picker for multiple matches, or falls
     * back to the "add the bot" instructions if none exist yet.
     */
    private void reconcilePendingChats(TelegramVerification session) {
        Long telegramUserId = session.getTelegramUserId();
        List<TelegramBotChat> chats = botChatRepo.findByTelegramUserIdOrderByCreatedAtDesc(telegramUserId);

        if (chats.isEmpty()) {
            sendText(telegramUserId,
                "Endi kanal yoki guruhingizni tasdiqlash uchun:\n" +
                "1. Kanalga/guruhga @tabrikoverifybot ni ENG KAM HUQUQLI admin qiling (barcha huquqlar O'CHIQ bo'lsin)\n" +
                "2. Bot avtomatik aniqlaydi va sizga xabar yuboradi\n" +
                "3. Botni kanal/guruhga admin qilib belgilagandan so'ng botga qayting va " + CHECK_COMMAND + " buyrug'ini yuboring"
            );
        } else if (chats.size() == 1) {
            applyChatToSession(session, toSnapshot(chats.get(0)));
        } else {
            sendChatPicker(telegramUserId, chats);
        }
    }

    private void sendChatPicker(Long telegramUserId, List<TelegramBotChat> chats) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (TelegramBotChat c : chats) {
            String label = c.getChatTitle() != null ? c.getChatTitle() : "@" + c.getChatUsername();
            InlineKeyboardButton btn = new InlineKeyboardButton(label);
            btn.setCallbackData("select_chat:" + c.getChatId());
            rows.add(List.of(btn));
        }
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup(rows);

        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(telegramUserId.toString());
        sendMessage.setText("Bot bir nechta kanal/guruhda admin ekan. Qaysi biri sizning arizangizga tegishli ekanini tanlang:");
        sendMessage.setReplyMarkup(markup);
        sendSafe(sendMessage);
    }

    private record ChatSnapshot(Long chatId, String title, String username, String type,
                                 Integer subscribers, String ownerStatus) {
    }

    private ChatSnapshot toSnapshot(TelegramBotChat c) {
        return new ChatSnapshot(c.getChatId(), c.getChatTitle(), c.getChatUsername(), c.getChatType(),
            c.getSubscribers(), c.getOwnerStatus());
    }

    private ChatSnapshot fetchChatSnapshot(Long chatId, Long telegramUserId, String chatType) throws TelegramApiException {
        GetChatMember getChatMember = new GetChatMember();
        getChatMember.setChatId(chatId.toString());
        getChatMember.setUserId(telegramUserId);
        ChatMember member = client.execute(getChatMember);

        String ownerStatus = member.getStatus();
        if (!"creator".equals(ownerStatus) && !"administrator".equals(ownerStatus)) {
            return null;
        }

        GetChatMemberCount getMemberCount = new GetChatMemberCount();
        getMemberCount.setChatId(chatId.toString());
        Integer count = client.execute(getMemberCount);

        GetChat getChat = new GetChat();
        getChat.setChatId(chatId.toString());
        Chat chatInfo = client.execute(getChat);

        return new ChatSnapshot(chatId, chatInfo.getTitle(), chatInfo.getUserName(), chatType, count, ownerStatus);
    }

    private void upsertBotChat(Long telegramUserId, ChatSnapshot snapshot) {
        TelegramBotChat botChat = botChatRepo.findByChatId(snapshot.chatId()).orElseGet(TelegramBotChat::new);
        botChat.setTelegramUserId(telegramUserId);
        botChat.setChatId(snapshot.chatId());
        botChat.setChatTitle(snapshot.title());
        botChat.setChatUsername(snapshot.username());
        botChat.setChatType(snapshot.type());
        botChat.setSubscribers(snapshot.subscribers());
        botChat.setOwnerStatus(snapshot.ownerStatus());
        botChat.setUpdatedAt(Instant.now());
        botChatRepo.save(botChat);
    }

    /**
     * Fired every time the bot's own membership status changes in a chat. Always records
     * admin promotions (regardless of the promoter's verification progress) so they can be
     * reconciled later via /tekshirish or the next successful phone link — see
     * reconcilePendingChats(). Demotions/removals drop the stored record.
     */
    private void handleMyChatMember(ChatMemberUpdated chatMemberUpdated) {
        ChatMember newMember = chatMemberUpdated.getNewChatMember();
        if (newMember == null) return;

        Long chatId = chatMemberUpdated.getChat().getId();
        String newStatus = newMember.getStatus();

        if (!"administrator".equals(newStatus) && !"creator".equals(newStatus)) {
            botChatRepo.deleteByChatId(chatId);
            return;
        }

        Long telegramUserId = chatMemberUpdated.getFrom().getId();
        TelegramVerification session = repo.findFirstByTelegramUserIdOrderByCreatedAtDesc(telegramUserId)
            .orElse(null);

        ChatSnapshot snapshot;
        try {
            snapshot = fetchChatSnapshot(chatId, telegramUserId, chatMemberUpdated.getChat().getType());
        } catch (TelegramApiException e) {
            log.error("Error reading channel info for telegramUserId={}, chatId={}", telegramUserId, chatId, e);
            if (session != null) {
                session.setStatus("FAILED");
                session.setUpdatedAt(Instant.now());
                repo.save(session);
            }
            sendText(telegramUserId, "Kanaldan ma'lumot olishda xatolik yuz berdi. Iltimos qayta urinib ko'ring.");
            return;
        }
        if (snapshot == null) {
            sendText(telegramUserId, "Siz bu kanal/guruhda admin emassiz. Iltimos admin huquqlarini tekshiring va qayta urinib ko'ring.");
            return;
        }

        upsertBotChat(telegramUserId, snapshot);

        if (session != null && "PHONE_LINKED".equals(session.getStatus())) {
            applyChatToSession(session, snapshot);
        } else {
            log.info("Recorded bot admin promotion for telegramUserId={}, chatId={} (session not ready: {})",
                telegramUserId, chatId, session == null ? "none" : session.getStatus());
            sendText(telegramUserId,
                "Bot ushbu kanal/guruhga admin qilib belgilandi va eslab qolindi. " +
                "Agar hali /start bosib telefon raqamingizni yubormagan bo'lsangiz, buni bajaring, so'ng botga " +
                CHECK_COMMAND + " buyrug'ini yuboring."
            );
        }
    }

    private void applyChatToSession(TelegramVerification session, ChatSnapshot snapshot) {
        session.setChatId(snapshot.chatId());
        session.setChatUsername(snapshot.username());
        session.setChatTitle(snapshot.title());
        session.setChatType(snapshot.type());
        session.setSubscribers(snapshot.subscribers());
        session.setOwnerStatus(snapshot.ownerStatus());
        session.setStatus("CHANNEL_READ");
        session.setUpdatedAt(Instant.now());
        repo.save(session);

        String title = snapshot.title() != null ? snapshot.title() : snapshot.username();
        String text = String.format("Aniqlandi: %s, %d obunachi, siz %s", title, snapshot.subscribers(), snapshot.ownerStatus());

        InlineKeyboardButton confirmBtn = new InlineKeyboardButton("Tasdiqlash / Yuborish");
        confirmBtn.setCallbackData("confirm_verification");
        List<List<InlineKeyboardButton>> inlineKb = new ArrayList<>();
        inlineKb.add(List.of(confirmBtn));
        InlineKeyboardMarkup inlineMarkup = new InlineKeyboardMarkup(inlineKb);

        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(session.getTelegramUserId().toString());
        sendMessage.setText(text);
        sendMessage.setReplyMarkup(inlineMarkup);
        sendSafe(sendMessage);
    }

    private void handleCallbackQuery(CallbackQuery callbackQuery) {
        String data = callbackQuery.getData();
        if (data == null) return;

        if ("confirm_verification".equals(data)) {
            handleConfirmVerification(callbackQuery);
        } else if (data.startsWith("select_chat:")) {
            handleSelectChat(callbackQuery, data.substring("select_chat:".length()));
        }
    }

    private void handleSelectChat(CallbackQuery callbackQuery, String chatIdStr) {
        Long telegramUserId = callbackQuery.getFrom().getId();
        Long chatId;
        try {
            chatId = Long.parseLong(chatIdStr);
        } catch (NumberFormatException e) {
            return;
        }

        TelegramVerification session = repo.findFirstByTelegramUserIdOrderByCreatedAtDesc(telegramUserId)
            .orElse(null);
        if (session == null || session.getPhone() == null) return;

        TelegramBotChat botChat = botChatRepo.findByChatId(chatId).orElse(null);
        if (botChat == null || !telegramUserId.equals(botChat.getTelegramUserId())) return;

        answerCallback(callbackQuery.getId());
        applyChatToSession(session, toSnapshot(botChat));
    }

    private void handleConfirmVerification(CallbackQuery callbackQuery) {
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

        answerCallback(callbackQuery.getId());

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

    private void answerCallback(String callbackQueryId) {
        try {
            AnswerCallbackQuery answer = new AnswerCallbackQuery();
            answer.setCallbackQueryId(callbackQueryId);
            client.execute(answer);
        } catch (TelegramApiException e) {
            log.warn("Failed to answer callback query {}", callbackQueryId, e);
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
