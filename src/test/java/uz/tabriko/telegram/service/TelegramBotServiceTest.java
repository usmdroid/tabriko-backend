package uz.tabriko.telegram.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChat;
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChatMember;
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChatMemberCount;
import org.telegram.telegrambots.meta.api.methods.groupadministration.LeaveChat;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.*;
import org.telegram.telegrambots.meta.api.objects.chatmember.ChatMember;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import uz.tabriko.domain.entity.ApplicationMessage;
import uz.tabriko.domain.entity.CreatorApplication;
import uz.tabriko.domain.enums.ApplicationStatus;
import uz.tabriko.domain.enums.MessageAuthor;
import uz.tabriko.repository.ApplicationMessageRepository;
import uz.tabriko.repository.CreatorApplicationRepository;
import uz.tabriko.telegram.entity.TelegramBotChat;
import uz.tabriko.telegram.entity.TelegramVerification;
import uz.tabriko.telegram.enums.TelegramOwnerStatus;
import uz.tabriko.telegram.enums.TelegramVerificationStatus;
import uz.tabriko.telegram.repository.TelegramBotChatRepository;
import uz.tabriko.telegram.repository.TelegramVerificationRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TelegramBotServiceTest {

    @Mock TelegramBotClient client;
    @Mock TelegramVerificationRepository repo;
    @Mock TelegramBotChatRepository botChatRepo;
    @Mock CreatorApplicationRepository applicationRepo;
    @Mock ApplicationMessageRepository messageRepo;

    @InjectMocks TelegramBotService service;

    // ===== Helpers =====

    private org.telegram.telegrambots.meta.api.objects.User tgUser(long id) {
        var u = new org.telegram.telegrambots.meta.api.objects.User();
        u.setId(id);
        u.setFirstName("Test");
        u.setIsBot(false);
        return u;
    }

    private Chat privateChat(long chatId) {
        var chat = new Chat();
        chat.setId(chatId);
        chat.setType("private");
        return chat;
    }

    private Update startUpdate(long userId, long chatId) {
        var msg = new Message();
        msg.setFrom(tgUser(userId));
        msg.setChat(privateChat(chatId));
        msg.setText("/start");
        var update = new Update();
        update.setUpdateId(1);
        update.setMessage(msg);
        return update;
    }

    private Update contactUpdate(long userId, long chatId, String phone) {
        var contact = new Contact();
        contact.setPhoneNumber(phone);
        contact.setUserId(userId);
        var msg = new Message();
        msg.setFrom(tgUser(userId));
        msg.setChat(privateChat(chatId));
        msg.setContact(contact);
        var update = new Update();
        update.setUpdateId(2);
        update.setMessage(msg);
        return update;
    }

    private Update myChatMemberUpdate(long userId, long channelId, String newStatus) {
        var chat = new Chat();
        chat.setId(channelId);
        chat.setType("channel");
        chat.setTitle("My Channel");

        var newMember = mock(ChatMember.class);
        when(newMember.getStatus()).thenReturn(newStatus);

        var cmu = mock(ChatMemberUpdated.class);
        when(cmu.getFrom()).thenReturn(tgUser(userId));
        when(cmu.getChat()).thenReturn(chat);
        when(cmu.getNewChatMember()).thenReturn(newMember);

        var update = new Update();
        update.setUpdateId(3);
        update.setMyChatMember(cmu);
        return update;
    }

    private Update callbackUpdate(long userId, String data) {
        var callbackQuery = mock(CallbackQuery.class);
        when(callbackQuery.getData()).thenReturn(data);
        when(callbackQuery.getFrom()).thenReturn(tgUser(userId));
        when(callbackQuery.getId()).thenReturn("cbq-1");

        var update = new Update();
        update.setUpdateId(4);
        update.setCallbackQuery(callbackQuery);
        return update;
    }

    // ===== handleStart =====

    @Test
    void handleStart_savesStartedSession_sendsPhoneRequest() throws TelegramApiException {
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.handleUpdate(startUpdate(100L, 200L));

        ArgumentCaptor<TelegramVerification> cap = ArgumentCaptor.forClass(TelegramVerification.class);
        verify(repo).save(cap.capture());
        assertThat(cap.getValue().getTelegramUserId()).isEqualTo(100L);
        assertThat(cap.getValue().getStatus()).isEqualTo(TelegramVerificationStatus.STARTED);

        ArgumentCaptor<SendMessage> msgCap = ArgumentCaptor.forClass(SendMessage.class);
        verify(client).execute(msgCap.capture());
        assertThat(msgCap.getValue().getText()).isEqualTo("Tekshiruvni boshladik");
        assertThat(msgCap.getValue().getChatId()).isEqualTo("200");
    }

    @Test
    void handleStart_nullFrom_noSaveNoSend() throws TelegramApiException {
        var msg = new Message();
        msg.setChat(privateChat(200L));
        msg.setText("/start");
        // from intentionally null

        var update = new Update();
        update.setUpdateId(1);
        update.setMessage(msg);

        service.handleUpdate(update);

        verify(repo, never()).save(any());
        verify(client, never()).execute(any(SendMessage.class));
    }

    // ===== handleContact — phone normalization + user linkage =====

    @Test
    void handleContact_phoneWithoutPlus_normalizedAndLinked() throws TelegramApiException {
        long userId = 100L;
        long chatId = 200L;

        TelegramVerification session = new TelegramVerification();
        session.setTelegramUserId(userId);
        session.setStatus(TelegramVerificationStatus.STARTED);
        when(repo.findFirstByTelegramUserIdOrderByCreatedAtDesc(userId)).thenReturn(Optional.of(session));

        when(applicationRepo.findFirstByPhoneOrderByCreatedAtDesc("+998901234567"))
                .thenReturn(Optional.of(new CreatorApplication()));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.handleUpdate(contactUpdate(userId, chatId, "998901234567"));

        assertThat(session.getPhone()).isEqualTo("+998901234567");
        assertThat(session.getStatus()).isEqualTo(TelegramVerificationStatus.PHONE_LINKED);
        verify(repo).save(session);
    }

    @Test
    void handleContact_phoneWithPlus_normalizedAndLinked() throws TelegramApiException {
        long userId = 100L;

        TelegramVerification session = new TelegramVerification();
        session.setTelegramUserId(userId);
        session.setStatus(TelegramVerificationStatus.STARTED);
        when(repo.findFirstByTelegramUserIdOrderByCreatedAtDesc(userId)).thenReturn(Optional.of(session));

        when(applicationRepo.findFirstByPhoneOrderByCreatedAtDesc("+998901234567"))
                .thenReturn(Optional.of(new CreatorApplication()));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.handleUpdate(contactUpdate(userId, 200L, "+998901234567"));

        assertThat(session.getPhone()).isEqualTo("+998901234567");
        assertThat(session.getStatus()).isEqualTo(TelegramVerificationStatus.PHONE_LINKED);
    }

    @Test
    void handleContact_phoneWithDashes_normalizedStripped() throws TelegramApiException {
        long userId = 100L;

        TelegramVerification session = new TelegramVerification();
        session.setTelegramUserId(userId);
        session.setStatus(TelegramVerificationStatus.STARTED);
        when(repo.findFirstByTelegramUserIdOrderByCreatedAtDesc(userId)).thenReturn(Optional.of(session));
        when(applicationRepo.findFirstByPhoneOrderByCreatedAtDesc("+99890123456")).thenReturn(Optional.empty());
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.handleUpdate(contactUpdate(userId, 200L, "998-90-123-456"));

        assertThat(session.getPhone()).isEqualTo("+99890123456");
        assertThat(session.getStatus()).isEqualTo(TelegramVerificationStatus.FAILED); // no application for this phone
    }

    @Test
    void handleContact_applicationNotFound_statusFailed_noLinkage() throws TelegramApiException {
        long userId = 100L;

        TelegramVerification session = new TelegramVerification();
        session.setTelegramUserId(userId);
        session.setStatus(TelegramVerificationStatus.STARTED);
        when(repo.findFirstByTelegramUserIdOrderByCreatedAtDesc(userId)).thenReturn(Optional.of(session));
        when(applicationRepo.findFirstByPhoneOrderByCreatedAtDesc(any())).thenReturn(Optional.empty());
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.handleUpdate(contactUpdate(userId, 200L, "998901234567"));

        assertThat(session.getStatus()).isEqualTo(TelegramVerificationStatus.FAILED);
    }

    @Test
    void handleContact_sessionNotFound_sendsStartPrompt() throws TelegramApiException {
        long userId = 100L;
        when(repo.findFirstByTelegramUserIdOrderByCreatedAtDesc(userId)).thenReturn(Optional.empty());

        service.handleUpdate(contactUpdate(userId, 200L, "998901234567"));

        verify(repo, never()).save(any());
        ArgumentCaptor<SendMessage> msgCap = ArgumentCaptor.forClass(SendMessage.class);
        verify(client).execute(msgCap.capture());
        assertThat(msgCap.getValue().getText()).contains("/start");
    }

    @Test
    void handleContact_sessionWrongStatus_sendsStartPrompt() throws TelegramApiException {
        long userId = 100L;

        TelegramVerification session = new TelegramVerification();
        session.setStatus(TelegramVerificationStatus.PHONE_LINKED); // not STARTED
        when(repo.findFirstByTelegramUserIdOrderByCreatedAtDesc(userId)).thenReturn(Optional.of(session));

        service.handleUpdate(contactUpdate(userId, 200L, "998901234567"));

        verify(repo, never()).save(any());
        ArgumentCaptor<SendMessage> msgCap = ArgumentCaptor.forClass(SendMessage.class);
        verify(client).execute(msgCap.capture());
        assertThat(msgCap.getValue().getText()).contains("/start");
    }

    // ===== Reconciling chats recorded before phone-linking (order-independent flow) =====

    private TelegramBotChat botChat(long userId, long chatId, String title) {
        TelegramBotChat c = new TelegramBotChat();
        c.setTelegramUserId(userId);
        c.setChatId(chatId);
        c.setChatTitle(title);
        c.setSubscribers(42);
        c.setOwnerStatus("administrator");
        return c;
    }

    @Test
    void handleContact_onePendingChat_autoConfirms() throws TelegramApiException {
        long userId = 100L;

        TelegramVerification session = new TelegramVerification();
        session.setTelegramUserId(userId);
        session.setStatus(TelegramVerificationStatus.STARTED);
        when(repo.findFirstByTelegramUserIdOrderByCreatedAtDesc(userId)).thenReturn(Optional.of(session));
        when(applicationRepo.findFirstByPhoneOrderByCreatedAtDesc("+998901234567"))
                .thenReturn(Optional.of(new CreatorApplication()));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(botChatRepo.findByTelegramUserIdOrderByCreatedAtDesc(userId))
                .thenReturn(List.of(botChat(userId, 300L, "Pending Channel")));

        service.handleUpdate(contactUpdate(userId, 200L, "998901234567"));

        assertThat(session.getStatus()).isEqualTo(TelegramVerificationStatus.CHANNEL_READ);
        assertThat(session.getChatId()).isEqualTo(300L);
        assertThat(session.getChatTitle()).isEqualTo("Pending Channel");
    }

    @Test
    void handleContact_multiplePendingChats_sendsPicker() throws TelegramApiException {
        long userId = 100L;

        TelegramVerification session = new TelegramVerification();
        session.setTelegramUserId(userId);
        session.setStatus(TelegramVerificationStatus.STARTED);
        when(repo.findFirstByTelegramUserIdOrderByCreatedAtDesc(userId)).thenReturn(Optional.of(session));
        when(applicationRepo.findFirstByPhoneOrderByCreatedAtDesc("+998901234567"))
                .thenReturn(Optional.of(new CreatorApplication()));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(botChatRepo.findByTelegramUserIdOrderByCreatedAtDesc(userId))
                .thenReturn(List.of(botChat(userId, 300L, "Channel A"), botChat(userId, 301L, "Channel B")));

        service.handleUpdate(contactUpdate(userId, 200L, "998901234567"));

        assertThat(session.getStatus()).isEqualTo(TelegramVerificationStatus.PHONE_LINKED); // not CHANNEL_READ until they pick one
        ArgumentCaptor<SendMessage> msgCap = ArgumentCaptor.forClass(SendMessage.class);
        verify(client, atLeastOnce()).execute(msgCap.capture());
        SendMessage picker = msgCap.getAllValues().stream()
                .filter(m -> m.getReplyMarkup() != null)
                .findFirst().orElseThrow();
        assertThat(picker.getReplyMarkup()).isInstanceOf(
                org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup.class);
    }

    // ===== /tekshirish manual re-check command =====

    private Update checkCommandUpdate(long userId, long chatId) {
        var msg = new Message();
        msg.setFrom(tgUser(userId));
        msg.setChat(privateChat(chatId));
        msg.setText("/tekshirish");
        var update = new Update();
        update.setUpdateId(5);
        update.setMessage(msg);
        return update;
    }

    @Test
    void checkCommand_noPhoneLinked_promptsStart() throws TelegramApiException {
        when(repo.findFirstByTelegramUserIdOrderByCreatedAtDesc(100L)).thenReturn(Optional.empty());

        service.handleUpdate(checkCommandUpdate(100L, 200L));

        ArgumentCaptor<SendMessage> msgCap = ArgumentCaptor.forClass(SendMessage.class);
        verify(client).execute(msgCap.capture());
        assertThat(msgCap.getValue().getText()).contains("/start");
    }

    @Test
    void checkCommand_alreadyVerified_saysSoWithoutReconciling() {
        TelegramVerification session = new TelegramVerification();
        session.setTelegramUserId(100L);
        session.setPhone("+998901234567");
        session.setStatus(TelegramVerificationStatus.VERIFIED);
        when(repo.findFirstByTelegramUserIdOrderByCreatedAtDesc(100L)).thenReturn(Optional.of(session));

        service.handleUpdate(checkCommandUpdate(100L, 200L));

        verify(botChatRepo, never()).findByTelegramUserIdOrderByCreatedAtDesc(any());
    }

    @Test
    void checkCommand_onePendingChat_autoConfirms() {
        TelegramVerification session = new TelegramVerification();
        session.setTelegramUserId(100L);
        session.setPhone("+998901234567");
        session.setStatus(TelegramVerificationStatus.PHONE_LINKED);
        when(repo.findFirstByTelegramUserIdOrderByCreatedAtDesc(100L)).thenReturn(Optional.of(session));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(botChatRepo.findByTelegramUserIdOrderByCreatedAtDesc(100L))
                .thenReturn(List.of(botChat(100L, 300L, "Pending Channel")));

        service.handleUpdate(checkCommandUpdate(100L, 200L));

        assertThat(session.getStatus()).isEqualTo(TelegramVerificationStatus.CHANNEL_READ);
        assertThat(session.getChatId()).isEqualTo(300L);
    }

    // ===== select_chat callback (picking one of several pending chats) =====

    @Test
    void selectChat_validChoice_appliesToSession() {
        long userId = 100L;

        TelegramVerification session = new TelegramVerification();
        session.setTelegramUserId(userId);
        session.setPhone("+998901234567");
        session.setStatus(TelegramVerificationStatus.STARTED);
        when(repo.findFirstByTelegramUserIdOrderByCreatedAtDesc(userId)).thenReturn(Optional.of(session));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(botChatRepo.findByChatId(301L)).thenReturn(Optional.of(botChat(userId, 301L, "Channel B")));

        service.handleUpdate(callbackUpdate(userId, "select_chat:301"));

        assertThat(session.getStatus()).isEqualTo(TelegramVerificationStatus.CHANNEL_READ);
        assertThat(session.getChatId()).isEqualTo(301L);
        assertThat(session.getChatTitle()).isEqualTo("Channel B");
    }

    @Test
    void selectChat_chatOwnedByAnotherUser_ignored() {
        long userId = 100L;

        TelegramVerification session = new TelegramVerification();
        session.setTelegramUserId(userId);
        session.setPhone("+998901234567");
        session.setStatus(TelegramVerificationStatus.STARTED);
        when(repo.findFirstByTelegramUserIdOrderByCreatedAtDesc(userId)).thenReturn(Optional.of(session));
        when(botChatRepo.findByChatId(301L)).thenReturn(Optional.of(botChat(999L, 301L, "Someone Else's Channel")));

        service.handleUpdate(callbackUpdate(userId, "select_chat:301"));

        assertThat(session.getStatus()).isEqualTo(TelegramVerificationStatus.STARTED);
        verify(repo, never()).save(any());
    }

    // ===== handleMyChatMember =====

    @Test
    void myChatMember_notAdmin_ignored() throws TelegramApiException {
        var update = myChatMemberUpdate(100L, 300L, "member");

        service.handleUpdate(update);

        verify(repo, never()).findFirstByTelegramUserIdOrderByCreatedAtDesc(any());
    }

    @Test
    void myChatMember_newMemberNull_ignored() {
        var cmu = mock(ChatMemberUpdated.class);
        when(cmu.getNewChatMember()).thenReturn(null);

        var update = new Update();
        update.setUpdateId(3);
        update.setMyChatMember(cmu);

        service.handleUpdate(update);

        verify(repo, never()).findFirstByTelegramUserIdOrderByCreatedAtDesc(any());
    }

    private void stubSuccessfulChatFetch(String ownerStatus, int subscribers, String title) throws TelegramApiException {
        ChatMember member = mock(ChatMember.class);
        when(member.getStatus()).thenReturn(ownerStatus);
        when(client.execute(any(GetChatMember.class))).thenReturn(member);
        when(client.execute(any(GetChatMemberCount.class))).thenReturn(subscribers);
        Chat chatInfo = new Chat();
        chatInfo.setTitle(title);
        when(client.execute(any(GetChat.class))).thenReturn(chatInfo);
    }

    @Test
    void myChatMember_sessionNotFound_recordsChatAndPromptsStart() throws TelegramApiException {
        when(repo.findFirstByTelegramUserIdOrderByCreatedAtDesc(100L)).thenReturn(Optional.empty());
        stubSuccessfulChatFetch("administrator", 10, "My Channel");

        service.handleUpdate(myChatMemberUpdate(100L, 300L, "administrator"));

        verify(botChatRepo).save(any(TelegramBotChat.class));
        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(client).execute(captor.capture());
        assertThat(captor.getValue().getText()).contains("/start");
    }

    @Test
    void myChatMember_sessionNotPhoneLinked_recordsChatAndPromptsStart() throws TelegramApiException {
        TelegramVerification session = new TelegramVerification();
        session.setStatus(TelegramVerificationStatus.STARTED);
        when(repo.findFirstByTelegramUserIdOrderByCreatedAtDesc(100L)).thenReturn(Optional.of(session));
        stubSuccessfulChatFetch("administrator", 10, "My Channel");

        service.handleUpdate(myChatMemberUpdate(100L, 300L, "administrator"));

        verify(botChatRepo).save(any(TelegramBotChat.class));
        assertThat(session.getStatus()).isEqualTo(TelegramVerificationStatus.STARTED); // unchanged — not reconciled yet
        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(client).execute(captor.capture());
        assertThat(captor.getValue().getText()).contains("/start");
    }

    @Test
    void myChatMember_demoted_deletesStoredChat() {
        service.handleUpdate(myChatMemberUpdate(100L, 300L, "member"));

        verify(botChatRepo).deleteByChatId(300L);
        verify(repo, never()).findFirstByTelegramUserIdOrderByCreatedAtDesc(any());
    }

    @Test
    void myChatMember_adminConfirmed_savesChannelRead() throws TelegramApiException {
        long userId = 100L;
        long channelId = 300L;

        TelegramVerification session = new TelegramVerification();
        session.setTelegramUserId(userId);
        session.setStatus(TelegramVerificationStatus.PHONE_LINKED);
        when(repo.findFirstByTelegramUserIdOrderByCreatedAtDesc(userId)).thenReturn(Optional.of(session));

        ChatMember member = mock(ChatMember.class);
        when(member.getStatus()).thenReturn("creator");
        when(client.execute(any(GetChatMember.class))).thenReturn(member);
        when(client.execute(any(GetChatMemberCount.class))).thenReturn(1500);

        Chat chatInfo = new Chat();
        chatInfo.setId(channelId);
        chatInfo.setTitle("My Channel");
        chatInfo.setUserName("mychannel");
        when(client.execute(any(GetChat.class))).thenReturn(chatInfo);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.handleUpdate(myChatMemberUpdate(userId, channelId, "administrator"));

        assertThat(session.getStatus()).isEqualTo(TelegramVerificationStatus.CHANNEL_READ);
        assertThat(session.getChatId()).isEqualTo(channelId);
        assertThat(session.getSubscribers()).isEqualTo(1500);
        assertThat(session.getChatTitle()).isEqualTo("My Channel");
        assertThat(session.getOwnerStatus()).isEqualTo(TelegramOwnerStatus.creator);
        verify(repo).save(session);
    }

    @Test
    void myChatMember_creatorStatus_alsoAccepted() throws TelegramApiException {
        long userId = 100L;

        TelegramVerification session = new TelegramVerification();
        session.setTelegramUserId(userId);
        session.setStatus(TelegramVerificationStatus.PHONE_LINKED);
        when(repo.findFirstByTelegramUserIdOrderByCreatedAtDesc(userId)).thenReturn(Optional.of(session));

        ChatMember member = mock(ChatMember.class);
        when(member.getStatus()).thenReturn("creator");
        when(client.execute(any(GetChatMember.class))).thenReturn(member);
        when(client.execute(any(GetChatMemberCount.class))).thenReturn(100);
        Chat chatInfo = new Chat();
        chatInfo.setId(300L);
        chatInfo.setTitle("Creator Channel");
        when(client.execute(any(GetChat.class))).thenReturn(chatInfo);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.handleUpdate(myChatMemberUpdate(userId, 300L, "creator"));

        assertThat(session.getStatus()).isEqualTo(TelegramVerificationStatus.CHANNEL_READ);
    }

    @Test
    void myChatMember_getChatMemberNotAdmin_sendsErrorMessage_statusUnchanged() throws TelegramApiException {
        long userId = 100L;

        TelegramVerification session = new TelegramVerification();
        session.setTelegramUserId(userId);
        session.setStatus(TelegramVerificationStatus.PHONE_LINKED);
        when(repo.findFirstByTelegramUserIdOrderByCreatedAtDesc(userId)).thenReturn(Optional.of(session));

        ChatMember member = mock(ChatMember.class);
        when(member.getStatus()).thenReturn("member");
        when(client.execute(any(GetChatMember.class))).thenReturn(member);

        service.handleUpdate(myChatMemberUpdate(userId, 300L, "administrator"));

        assertThat(session.getStatus()).isEqualTo(TelegramVerificationStatus.PHONE_LINKED); // unchanged
        ArgumentCaptor<SendMessage> msgCap = ArgumentCaptor.forClass(SendMessage.class);
        verify(client).execute(msgCap.capture());
        assertThat(msgCap.getValue().getText()).contains("admin emassiz");
    }

    @Test
    void myChatMember_getChatMemberThrows_savesFailedAndSendsError() throws TelegramApiException {
        long userId = 100L;

        TelegramVerification session = new TelegramVerification();
        session.setTelegramUserId(userId);
        session.setStatus(TelegramVerificationStatus.PHONE_LINKED);
        when(repo.findFirstByTelegramUserIdOrderByCreatedAtDesc(userId)).thenReturn(Optional.of(session));
        when(client.execute(any(GetChatMember.class))).thenThrow(new TelegramApiException("API error"));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.handleUpdate(myChatMemberUpdate(userId, 300L, "administrator"));

        assertThat(session.getStatus()).isEqualTo(TelegramVerificationStatus.FAILED);
        verify(repo).save(session);
        ArgumentCaptor<SendMessage> msgCap = ArgumentCaptor.forClass(SendMessage.class);
        verify(client).execute(msgCap.capture());
        assertThat(msgCap.getValue().getText()).contains("xatolik");
    }

    // ===== handleCallbackQuery + state machine CHANNEL_READ → SUBMITTED → DONE =====

    @Test
    void callbackQuery_wrongData_ignored() {
        service.handleUpdate(callbackUpdate(100L, "some_other_data"));
        verify(repo, never()).findFirstByTelegramUserIdOrderByCreatedAtDesc(any());
    }

    @Test
    void callbackQuery_sessionNotFound_ignored() {
        long userId = 100L;
        when(repo.findFirstByTelegramUserIdOrderByCreatedAtDesc(userId)).thenReturn(Optional.empty());

        service.handleUpdate(callbackUpdate(userId, "confirm_verification"));

        verify(repo, never()).save(any());
    }

    @Test
    void callbackQuery_sessionNotChannelRead_ignored() {
        long userId = 100L;

        TelegramVerification session = new TelegramVerification();
        session.setStatus(TelegramVerificationStatus.STARTED);
        when(repo.findFirstByTelegramUserIdOrderByCreatedAtDesc(userId)).thenReturn(Optional.of(session));

        service.handleUpdate(callbackUpdate(userId, "confirm_verification"));

        verify(repo, never()).save(any());
    }

    @Test
    void callbackQuery_confirmVerification_verifiedThenLeaves() throws TelegramApiException {
        long userId = 100L;
        long channelId = 300L;

        TelegramVerification session = new TelegramVerification();
        session.setTelegramUserId(userId);
        session.setChatId(channelId);
        session.setStatus(TelegramVerificationStatus.CHANNEL_READ);
        when(repo.findFirstByTelegramUserIdOrderByCreatedAtDesc(userId)).thenReturn(Optional.of(session));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.handleUpdate(callbackUpdate(userId, "confirm_verification"));

        assertThat(session.getStatus()).isEqualTo(TelegramVerificationStatus.VERIFIED);
        assertThat(session.getVerifiedAt()).isNotNull();
        verify(client).execute(any(LeaveChat.class));
    }

    @Test
    void callbackQuery_chatIdNull_doneWithoutLeaveChat() throws TelegramApiException {
        long userId = 100L;

        TelegramVerification session = new TelegramVerification();
        session.setTelegramUserId(userId);
        session.setChatId(null);
        session.setStatus(TelegramVerificationStatus.CHANNEL_READ);
        when(repo.findFirstByTelegramUserIdOrderByCreatedAtDesc(userId)).thenReturn(Optional.of(session));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.handleUpdate(callbackUpdate(userId, "confirm_verification"));

        assertThat(session.getStatus()).isEqualTo(TelegramVerificationStatus.VERIFIED);
        verify(client, never()).execute(any(LeaveChat.class));
    }

    @Test
    void callbackQuery_leaveChatThrows_stillVerified() throws TelegramApiException {
        long userId = 100L;

        TelegramVerification session = new TelegramVerification();
        session.setTelegramUserId(userId);
        session.setChatId(300L);
        session.setStatus(TelegramVerificationStatus.CHANNEL_READ);
        when(repo.findFirstByTelegramUserIdOrderByCreatedAtDesc(userId)).thenReturn(Optional.of(session));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(client.execute(any(LeaveChat.class))).thenThrow(new TelegramApiException("Network error"));

        service.handleUpdate(callbackUpdate(userId, "confirm_verification"));

        // Leaving the channel is best-effort cleanup — a failure must not un-verify.
        assertThat(session.getStatus()).isEqualTo(TelegramVerificationStatus.VERIFIED);
    }

    // ===== Full state machine: STARTED → PHONE_LINKED → CHANNEL_READ → SUBMITTED → DONE =====

    @Test
    void stateMachine_fullFlow_startedToDone() throws TelegramApiException {
        long userId = 100L;
        long chatId = 200L;
        long channelId = 300L;

        // Step 1: /start
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        service.handleUpdate(startUpdate(userId, chatId));

        ArgumentCaptor<TelegramVerification> startCap = ArgumentCaptor.forClass(TelegramVerification.class);
        verify(repo, atLeastOnce()).save(startCap.capture());
        TelegramVerification session = startCap.getValue();
        assertThat(session.getStatus()).isEqualTo(TelegramVerificationStatus.STARTED);

        // Step 2: contact phone
        reset(repo, client, applicationRepo);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(repo.findFirstByTelegramUserIdOrderByCreatedAtDesc(userId)).thenReturn(Optional.of(session));
        when(applicationRepo.findFirstByPhoneOrderByCreatedAtDesc("+998901234567"))
                .thenReturn(Optional.of(new CreatorApplication()));

        service.handleUpdate(contactUpdate(userId, chatId, "998901234567"));
        assertThat(session.getStatus()).isEqualTo(TelegramVerificationStatus.PHONE_LINKED);

        // Step 3: my_chat_member — bot added as admin
        reset(repo, client);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(repo.findFirstByTelegramUserIdOrderByCreatedAtDesc(userId)).thenReturn(Optional.of(session));
        ChatMember member = mock(ChatMember.class);
        when(member.getStatus()).thenReturn("administrator");
        when(client.execute(any(GetChatMember.class))).thenReturn(member);
        when(client.execute(any(GetChatMemberCount.class))).thenReturn(500);
        Chat channelInfo = new Chat();
        channelInfo.setId(channelId);
        channelInfo.setTitle("Test Channel");
        when(client.execute(any(GetChat.class))).thenReturn(channelInfo);

        service.handleUpdate(myChatMemberUpdate(userId, channelId, "administrator"));
        assertThat(session.getStatus()).isEqualTo(TelegramVerificationStatus.CHANNEL_READ);
        assertThat(session.getChatId()).isEqualTo(channelId);

        // Step 4: confirm_verification callback
        reset(repo, client);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(repo.findFirstByTelegramUserIdOrderByCreatedAtDesc(userId)).thenReturn(Optional.of(session));

        service.handleUpdate(callbackUpdate(userId, "confirm_verification"));
        assertThat(session.getStatus()).isEqualTo(TelegramVerificationStatus.VERIFIED);
        assertThat(session.getVerifiedAt()).isNotNull();
        verify(client).execute(any(LeaveChat.class));
    }

    // ===== handleApplicantReply — must target the INFO_REQUESTED app, not merely the newest =====

    private Update textUpdate(long userId, long chatId, String text) {
        var msg = new Message();
        msg.setFrom(tgUser(userId));
        msg.setChat(privateChat(chatId));
        msg.setText(text);
        var update = new Update();
        update.setUpdateId(6);
        update.setMessage(msg);
        return update;
    }

    @Test
    void applicantReply_multipleApplicationsForPhone_savesToInfoRequestedOne() throws TelegramApiException {
        long userId = 100L;
        long chatId = 200L;
        String phone = "+998901234567";

        TelegramVerification session = new TelegramVerification();
        session.setTelegramUserId(userId);
        session.setPhone(phone);
        when(repo.findFirstByTelegramUserIdOrderByCreatedAtDesc(userId)).thenReturn(Optional.of(session));

        // Old REJECTED application and a newer application of another status both exist for
        // this phone — the newest-by-phone lookup would incorrectly pick one of these.
        CreatorApplication newestNonMatchingApp = new CreatorApplication();
        newestNonMatchingApp.setStatus(ApplicationStatus.SUBMITTED);
        when(applicationRepo.findFirstByPhoneOrderByCreatedAtDesc(phone))
                .thenReturn(Optional.of(newestNonMatchingApp));

        CreatorApplication infoRequestedApp = new CreatorApplication();
        infoRequestedApp.setStatus(ApplicationStatus.INFO_REQUESTED);
        when(applicationRepo.findFirstByPhoneAndStatusOrderByCreatedAtDesc(phone, ApplicationStatus.INFO_REQUESTED))
                .thenReturn(Optional.of(infoRequestedApp));

        service.handleUpdate(textUpdate(userId, chatId, "Mana mening javobim"));

        ArgumentCaptor<ApplicationMessage> savedCap = ArgumentCaptor.forClass(ApplicationMessage.class);
        verify(messageRepo).save(savedCap.capture());
        assertThat(savedCap.getValue().getApplication()).isSameAs(infoRequestedApp);
        assertThat(savedCap.getValue().getAuthor()).isEqualTo(MessageAuthor.APPLICANT);
        assertThat(savedCap.getValue().getText()).isEqualTo("Mana mening javobim");

        ArgumentCaptor<SendMessage> sendCap = ArgumentCaptor.forClass(SendMessage.class);
        verify(client).execute(sendCap.capture());
        assertThat(sendCap.getValue().getText()).isEqualTo("Javobingiz qabul qilindi. Rahmat!");
    }

    @Test
    void applicantReply_noInfoRequestedApp_sendsPoliteMessage() throws TelegramApiException {
        long userId = 100L;
        long chatId = 200L;
        String phone = "+998901234567";

        TelegramVerification session = new TelegramVerification();
        session.setTelegramUserId(userId);
        session.setPhone(phone);
        when(repo.findFirstByTelegramUserIdOrderByCreatedAtDesc(userId)).thenReturn(Optional.of(session));
        when(applicationRepo.findFirstByPhoneAndStatusOrderByCreatedAtDesc(phone, ApplicationStatus.INFO_REQUESTED))
                .thenReturn(Optional.empty());

        service.handleUpdate(textUpdate(userId, chatId, "Salom"));

        verify(messageRepo, never()).save(any());
        ArgumentCaptor<SendMessage> sendCap = ArgumentCaptor.forClass(SendMessage.class);
        verify(client).execute(sendCap.capture());
        assertThat(sendCap.getValue().getText()).isEqualTo("Hozircha javob talab qilinmagan. Rahmat!");
    }

    // ===== Error resilience =====

    @Test
    void handleUpdate_unexpectedException_swallowed() {
        when(repo.findFirstByTelegramUserIdOrderByCreatedAtDesc(any())).thenThrow(new RuntimeException("DB down"));

        // Should not throw
        service.handleUpdate(contactUpdate(100L, 200L, "998901234567"));
    }
}
