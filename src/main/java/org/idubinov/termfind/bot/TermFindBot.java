package org.idubinov.termfind.bot;

import org.idubinov.termfind.models.Entry;
import org.idubinov.termfind.models.Term;
import org.idubinov.termfind.ai.SummaryService;
import org.idubinov.termfind.service.EntryService;
import org.idubinov.termfind.service.SearchService;
import org.idubinov.termfind.service.SubjectService;
import org.idubinov.termfind.service.UserService;
import org.idubinov.termfind.util.PdfPageRenderer;
import java.io.File;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Telegram-бот: пользователь шлет термин — бот возвращает определения и упоминания
 * (отдельными сообщениями, полными текстами). Для админа — кнопки ✅ на каждое
 * вхождение и 🔒 финализация термина. Long polling стартует по ApplicationReadyEvent.
 * Команды: /start, /help, /subjects; любой текст без «/» считается поисковым запросом.
 */
@Component
public class TermFindBot extends TelegramLongPollingBot {

    private static final Logger log = LoggerFactory.getLogger(TermFindBot.class);

    private static final String UNKNOWN_COMMAND =
            "Не знаю такой команды. Просто пришли термин текстом или /help.";

    private final BotConfig config;
    private final SearchService searchService;
    private final EntryService entryService;
    private final SummaryService summaryService;
    private final UserService userService;
    private final SubjectService subjectService;
    /** LLM отвечает 5-20 сек — обрабатываем кнопку вне потока polling. */
    private final ExecutorService aiExecutor = Executors.newFixedThreadPool(2);

    public TermFindBot(BotConfig config, SearchService searchService, EntryService entryService,
                       SummaryService summaryService, UserService userService, SubjectService subjectService) {
        this.config = config;
        this.searchService = searchService;
        this.entryService = entryService;
        this.summaryService = summaryService;
        this.userService = userService;
        this.subjectService = subjectService;
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
                Message message = update.getMessage();
                if (message.getFrom() != null) {
                    userService.getOrCreate(message.getFrom().getId());
                }
                String text = message.getText().trim();
                if (text.startsWith("/")) {
                    handleCommand(text, message.getChatId());
                } else {
                    handleSearch(message);
                }
            } else if (update.hasCallbackQuery()) {
                handleCallback(update.getCallbackQuery());
            }
        } catch (Exception e) {
            log.error("Ошибка обработки апдейта от Telegram", e);
        }
    }

    private void handleCommand(String text, long chatId) {
        // в группах команда приходит с суффиксом: /help@TermSearchBot
        String command = text.split("\\s+")[0].toLowerCase();
        int at = command.indexOf('@');
        if (at > 0) command = command.substring(0, at);

        switch (command) {
            case "/start", "/help" -> executeSilently(SendMessage.builder()
                    .chatId(chatId)
                    .text(BotMessageFormatter.buildWelcome())
                    .build());
            case "/subjects" -> executeSilently(SendMessage.builder()
                    .chatId(chatId)
                    .text(BotMessageFormatter.buildSubjects(subjectService.listWithBookCounts()))
                    .build());
            default -> executeSilently(SendMessage.builder()
                    .chatId(chatId)
                    .text(UNKNOWN_COMMAND)
                    .build());
        }
    }

    private void handleSearch(Message message) {
        String term = message.getText().trim();
        long chatId = message.getChatId();

        List<Entry> entries = searchService.search(term);
        boolean isAdmin = chatId == config.adminId();
        Optional<Term> termEntity = entryService.findTermByQuery(term);
        boolean finalized = termEntity.map(Term::isFinalized).orElse(false);

        // Показываем пользователю только отфильтрованное и отсортированное;
        // кнопки модерации — по всем вхождениям (админ видит и то, что фильтр отбросил)
        List<Entry> presentable = entryService.presentable(term);

        InlineKeyboardMarkup keyboard = BotMessageFormatter.buildModerationKeyboard(entries, chatId,
                config.adminId(), finalized ? null : termEntity.map(Term::getId).orElse(null));
        keyboard = withExplainButton(keyboard, termEntity.map(Term::getId).orElse(null));
        SendMessage header = SendMessage.builder()
                .chatId(chatId)
                .text(BotMessageFormatter.buildCard(term, presentable, finalized))
                .parseMode(ParseMode.HTML)
                .build();
        if (keyboard != null) {
            header.setReplyMarkup(keyboard);
        }
        sendWithHtmlFallback(header);

        // Файл с полными текстами отфильтрованных вхождений
        if (!presentable.isEmpty()) {
            sendDocument(chatId, ReportExporter.fileName(term),
                    ReportExporter.export(term, presentable, entries.size() - presentable.size()));
        }
    }

    /** Отправка с HTML; если Telegram не принял разметку — повтор без parseMode. */
    private void sendWithHtmlFallback(SendMessage message) {
        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.warn("Не удалось отправить с HTML-разметкой, повторяю без неё: {}", e.getMessage());
            try {
                execute(SendMessage.builder()
                        .chatId(message.getChatId())
                        .text(message.getText())
                        .build());
            } catch (TelegramApiException e2) {
                log.error("Не удалось отправить сообщение", e2);
            }
        }
    }

    private void sendDocument(long chatId, String fileName, byte[] content) {
        try {
            execute(SendDocument.builder()
                    .chatId(chatId)
                    .document(new InputFile(new java.io.ByteArrayInputStream(content), fileName))
                    .build());
        } catch (TelegramApiException e) {
            log.error("Не удалось отправить файл отчета", e);
        }
    }

    private void handleCallback(CallbackQuery callbackQuery) {
        String data = callbackQuery.getData();
        if (data == null) return;

        if (data.startsWith("explain:")) {
            long termId = Long.parseLong(data.substring("explain:".length()));
            handleExplain(callbackQuery, termId);
            return;
        }

        if (data.startsWith("page:")) {
            // просмотр страницы учебника доступен всем
            long entryId = Long.parseLong(data.substring("page:".length()));
            handleViewPage(callbackQuery, entryId);
            return;
        }

        if (callbackQuery.getFrom().getId() != config.adminId()) {
            return; // модерировать может только админ
        }

        if (data.startsWith("approve:")) {
            long entryId = Long.parseLong(data.substring("approve:".length()));
            boolean approved = entryService.approveEntry(entryId);
            answerCallback(callbackQuery.getId(),
                    approved ? "✅ Подтверждено — можно отметить ещё" : "Нельзя: термин финализирован или не найден");

            // Текст карточки не трогаем; из клавиатуры убираем только нажатую кнопку
            if (approved && callbackQuery.getMessage() != null) {
                updateModerationKeyboard(callbackQuery.getMessage(), entryId);
            }
        } else if (data.startsWith("finalize:")) {
            long termId = Long.parseLong(data.substring("finalize:".length()));
            boolean finalized = entryService.finalizeTerm(termId);
            if (finalized) summaryService.invalidateCache(termId);
            answerCallback(callbackQuery.getId(), finalized ? "🔒 Термин финализирован" : "Не найдено");
            if (finalized && callbackQuery.getMessage() != null) {
                editMessage(callbackQuery.getMessage().getMessageId(),
                        callbackQuery.getMessage().getChatId(),
                        "🔒 Термин финализирован: неподтвержденные вхождения удалены, " +
                                "остались только ✅. Аппрувы больше недоступны.");
            }
        }
    }

    /** Нейро-объяснение: генерация в отдельном потоке, ответ новым сообщением. */
    private void handleExplain(CallbackQuery callbackQuery, long termId) {
        long chatId = callbackQuery.getMessage().getChatId();
        entryService.findTermById(termId).ifPresentOrElse(term -> {
            String termName = term.getDisplayForm();
            answerCallback(callbackQuery.getId(), "🧠 Думаю… это займет до 20 секунд");
            aiExecutor.submit(() -> {
                try {
                    var explanation = summaryService.explain(termName);
                    if (explanation.isEmpty()) {
                        executeSilently(SendMessage.builder().chatId(chatId)
                                .text("❌ Недостаточно данных для объяснения «" + termName + "»").build());
                        return;
                    }
                    String text = "🧠 " + explanation.get().text()
                            + "\n\n📚 Источники: " + explanation.get().sources();
                    executeSilently(SendMessage.builder().chatId(chatId)
                            .text(BotMessageFormatter.esc(text)).parseMode(ParseMode.HTML).build());
                } catch (Exception e) {
                    log.error("Ошибка генерации объяснения", e);
                    executeSilently(SendMessage.builder().chatId(chatId)
                            .text("⚠️ Нейросеть недоступна, попробуй позже").build());
                }
            });
        }, () -> answerCallback(callbackQuery.getId(), "Термин не найден"));
    }

    private InlineKeyboardMarkup withExplainButton(InlineKeyboardMarkup keyboard, Long termId) {
        if (!summaryService.isEnabled() || termId == null) return keyboard;
        List<List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton>> rows =
                new java.util.ArrayList<>(keyboard != null ? keyboard.getKeyboard() : List.of());
        rows.add(0, List.of(org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton.builder()
                .text("🧠 Объяснить")
                .callbackData("explain:" + termId)
                .build()));
        return new InlineKeyboardMarkup(rows);
    }

    /** Рендерит страницу учебника и шлет фото. */
    private void handleViewPage(CallbackQuery callbackQuery, long entryId) {
        Optional<Entry> entryOpt = entryService.findEntryWithBook(entryId);
        if (entryOpt.isEmpty()) {
            answerCallback(callbackQuery.getId(), "Вхождение не найдено");
            return;
        }
        Entry entry = entryOpt.get();
        try {
            byte[] png = new PdfPageRenderer().renderPage(new File(entry.getBook().getPdfPath()),
                    entry.getPageNumber());
            execute(SendPhoto.builder()
                    .chatId(callbackQuery.getMessage().getChatId())
                    .photo(new InputFile(new java.io.ByteArrayInputStream(png), "page_" + entry.getPageNumber() + ".png"))
                    .caption("📖 " + entry.getBook().getTitle() + ", стр. " + entry.getPageNumber())
                    .build());
            answerCallback(callbackQuery.getId(), null);
        } catch (Exception e) {
            log.error("Не удалось отрисовать страницу", e);
            answerCallback(callbackQuery.getId(), "Не удалось отрисовать страницу");
        }
    }

    /** Перестраивает клавиатуру сообщения: без только что подтвержденной кнопки. */
    private void updateModerationKeyboard(Message message, long approvedEntryId) {
        try {
            Optional<Entry> approvedEntry = entryService.findEntryWithBook(approvedEntryId);
            if (approvedEntry.isEmpty()) return;

            Term term = approvedEntry.get().getTerm();
            List<Entry> remaining = entryService.findNotApprovedEntriesByTerm(term.getDisplayForm());
            InlineKeyboardMarkup newKeyboard = BotMessageFormatter.buildModerationKeyboard(
                    remaining, config.adminId(), config.adminId(),
                    term.isFinalized() ? null : term.getId());

            execute(EditMessageReplyMarkup.builder()
                    .chatId(message.getChatId())
                    .messageId(message.getMessageId())
                    .replyMarkup(newKeyboard)
                    .build());
        } catch (TelegramApiException e) {
            log.error("Не удалось обновить клавиатуру", e);
        }
    }

    private void answerCallback(String callbackId, String text) {
        executeSilently(AnswerCallbackQuery.builder()
                .callbackQueryId(callbackId)
                .text(text)
                .showAlert(false)
                .build());
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
