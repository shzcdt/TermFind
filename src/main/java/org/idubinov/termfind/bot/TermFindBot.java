package org.idubinov.termfind.bot;

import org.idubinov.termfind.models.Entry;
import org.idubinov.termfind.service.EntryService;
import org.idubinov.termfind.service.SearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.util.ArrayList;
import java.util.List;

/**
 * Telegram-бот: пользователь шлет термин — бот возвращает определения и упоминания.
 * Для админа у определений есть кнопка «Подтвердить» (модерация).
 * Long polling стартует в конструкторе; при пустом токене бот отключен.
 */
@Component
public class TermFindBot extends TelegramLongPollingBot {

    private static final Logger log = LoggerFactory.getLogger(TermFindBot.class);

    private final BotConfig config;
    private final SearchService searchService;
    private final EntryService entryService;

    public TermFindBot(BotConfig config, SearchService searchService, EntryService entryService) {
        this.config = config;
        this.searchService = searchService;
        this.entryService = entryService;
        // ВНИМАНИЕ: не регистрируем бота в конструкторе — long polling начнет обрабатывать
        // апдейты до готовности Spring-контекста (LazyInitialization/IllegalStateException).
    }

    /** Регистрация long polling — только когда контекст полностью готов. */
    @EventListener(ApplicationReadyEvent.class)
    public void startPolling() {
        if (config.token() == null || config.token().isBlank()) {
            log.warn("BOT_TOKEN не задан — Telegram-бот отключен, REST продолжает работать");
            return;
        }
        try {
            new org.telegram.telegrambots.meta.TelegramBotsApi(DefaultBotSession.class).registerBot(this);
            log.info("Telegram-бот @{} запущен", config.username());
        } catch (TelegramApiException e) {
            log.error("Не удалось зарегистрировать бота", e);
        }
    }

    @Override
    public String getBotUsername() {
        return config.username();
    }

    @Override
    public String getBotToken() {
        return config.token();
    }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            if (update.hasMessage() && update.getMessage().hasText()) {
                handleSearch(update.getMessage());
            } else if (update.hasCallbackQuery()) {
                handleApprove(update.getCallbackQuery());
            }
        } catch (Exception e) {
            // ошибка одного апдейта не должна ронять поток polling
            log.error("Ошибка обработки апдейта от Telegram", e);
        }
    }

    private void handleSearch(Message message) {
        String term = message.getText().trim();
        long chatId = message.getChatId();

        List<Entry> entries = searchService.search(term);

        SendMessage sendMessage = SendMessage.builder()
                .chatId(chatId)
                .text(BotMessageFormatter.formatAnswer(term, entries))
                .build();

        InlineKeyboardMarkup keyboard = BotMessageFormatter.approveKeyboard(entries, chatId, config.adminId());
        if (keyboard != null) {
            sendMessage.setReplyMarkup(keyboard);
        }
        executeSilently(sendMessage);
    }

    private void handleApprove(CallbackQuery callbackQuery) {
        if (callbackQuery.getFrom().getId() != config.adminId()) {
            return; // модерировать может только админ
        }

        String data = callbackQuery.getData(); // формат "approve:<id>"
        if (data == null || !data.startsWith("approve:")) return;
        long entryId = Long.parseLong(data.substring("approve:".length()));

        boolean approved = entryService.approveEntry(entryId);

        executeSilently(AnswerCallbackQuery.builder()
                .callbackQueryId(callbackQuery.getId())
                .text(approved ? "✅ Подтверждено" : "Не найдено")
                .showAlert(false)
                .build());

        if (approved && callbackQuery.getMessage() != null) {
            editMessage(callbackQuery.getMessage().getMessageId(),
                    callbackQuery.getMessage().getChatId(),
                    "✅ Вхождение " + entryId + " подтверждено");
        }
    }

    private void executeSilently(SendMessage message) {
        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Не удалось отправить сообщение", e);
        }
    }

    private void executeSilently(AnswerCallbackQuery query) {
        try {
            execute(query);
        } catch (TelegramApiException e) {
            log.error("Не удалось ответить на callback", e);
        }
    }

    private void editMessage(Integer messageId, Long chatId, String newText) {
        try {
            execute(EditMessageText.builder()
                    .chatId(chatId)
                    .messageId(messageId)
                    .text(newText)
                    .build());
        } catch (TelegramApiException e) {
            log.error("Не удалось отредактировать сообщение", e);
        }
    }

}
