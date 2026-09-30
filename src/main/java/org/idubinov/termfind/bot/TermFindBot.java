package org.idubinov.termfind.bot;

import org.idubinov.termfind.models.BookRequest;
import org.idubinov.termfind.models.Entry;
import org.idubinov.termfind.models.Term;
import org.idubinov.termfind.ai.SummaryService;
import org.idubinov.termfind.service.BookService;
import org.idubinov.termfind.service.EntryService;
import org.idubinov.termfind.service.FeedbackService;
import org.idubinov.termfind.service.SearchService;
import org.idubinov.termfind.service.SubjectService;
import org.idubinov.termfind.service.UserService;
import org.idubinov.termfind.service.WordService;
import org.idubinov.termfind.util.PdfPageRenderer;
import org.idubinov.termfind.util.TermNormalizer;
import org.idubinov.termfind.util.TocMapper;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Telegram-бот: пользователь шлет термин — бот возвращает определения и упоминания
 * (отдельными сообщениями, полными текстами). Для админа — кнопки ✅ на каждое
 * вхождение и 🔒 финализация термина. Long polling стартует по ApplicationReadyEvent.
 * Команды: /start, /help, /subjects, /upload; любой текст без «/» считается
 * поисковым запросом. Поиск нового термина и загрузка PDF идут в фоновых пулах.
 */
@Component
public class TermFindBot extends TelegramLongPollingBot {

    private static final Logger log = LoggerFactory.getLogger(TermFindBot.class);

    private static final String UNKNOWN_COMMAND =
            "Не знаю такой команды. Просто пришли термин текстом или /help.";
    /** Telegram ограничивает скачивание файлов ботами 20 МБ. */
    private static final long MAX_PDF_BYTES = 20L * 1024 * 1024;
    private static final Path UPLOADS_DIR = Path.of("uploads");

    private final BotConfig config;
    private final SearchService searchService;
    private final EntryService entryService;
    private final SummaryService summaryService;
    private final UserService userService;
    private final SubjectService subjectService;
    private final BookService bookService;
    private final FeedbackService feedbackService;
    private final WordService wordService;
    /** LLM отвечает 5-20 сек — обрабатываем кнопку вне потока polling. */
    private final ExecutorService aiExecutor = Executors.newFixedThreadPool(2);
    /** Индексация нового термина (сотни страниц) — вне потока polling. */
    private final ExecutorService indexingExecutor = Executors.newFixedThreadPool(2);
    /** Извлечение одобренной книги (страницы + определения + TOC) — фон. */
    private final ExecutorService extractionExecutor = Executors.newFixedThreadPool(2);
    /** chatId -> выбранный предмет в диалоге /upload. */
    private final Map<Long, Long> uploadSubjectPick = new ConcurrentHashMap<>();
    /** normalizedTerm -> true, пока идёт индексация (дедуп параллельных запросов). */
    private final Map<String, Boolean> inFlightSearch = new ConcurrentHashMap<>();
    /** termId -> true, пока идут LLM-экстры (классификация + синтез). */
    private final Map<Long, Boolean> synthesisInFlight = new ConcurrentHashMap<>();

    public TermFindBot(BotConfig config, SearchService searchService, EntryService entryService,
                       SummaryService summaryService, UserService userService, SubjectService subjectService,
                       BookService bookService, FeedbackService feedbackService, WordService wordService) {
        this.config = config;
        this.searchService = searchService;
        this.entryService = entryService;
        this.summaryService = summaryService;
        this.userService = userService;
        this.subjectService = subjectService;
        this.bookService = bookService;
        this.feedbackService = feedbackService;
        this.wordService = wordService;
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
            } else if (update.hasMessage() && update.getMessage().hasDocument()) {
                handleDocument(update.getMessage());
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
            case "/upload" -> executeSilently(SendMessage.builder()
                    .chatId(chatId)
                    .text("📚 Выбери предмет для новой книги:")
                    .replyMarkup(BotMessageFormatter.buildSubjectPickerKeyboard(subjectService.listWithBookCounts()))
                    .build());
            case "/pending" -> handlePending(chatId);
            default -> executeSilently(SendMessage.builder()
                    .chatId(chatId)
                    .text(UNKNOWN_COMMAND)
                    .build());
        }
    }

    private void handleSearch(Message message) {
        String term = message.getText().trim();
        long chatId = message.getChatId();
        long viewerId = message.getFrom() != null ? message.getFrom().getId() : chatId;

        // известный термин — карточка сразу; новый — индексация в фоне
        if (entryService.findTermByQuery(term).isPresent()) {
            presentSearch(viewerId, chatId, term);
            return;
        }

        String normalized = TermNormalizer.normalize(term);
        if (normalized.isEmpty()) return;
        if (inFlightSearch.putIfAbsent(normalized, true) != null) {
            executeSilently(SendMessage.builder().chatId(chatId)
                    .text("⏳ «" + term + "» уже ищется, скоро пришлю результат").build());
            return;
        }

        executeSilently(SendMessage.builder().chatId(chatId)
                .text("🔍 Ищу «" + term + "» по книгам… впервые это занимает до минуты").build());
        indexingExecutor.submit(() -> {
            try {
                searchService.search(term);
                presentSearch(viewerId, chatId, term);
            } catch (Exception e) {
                log.error("Ошибка индексации термина «{}»", term, e);
                executeSilently(SendMessage.builder().chatId(chatId)
                        .text("⚠️ Не удалось проиндексировать «" + term + "», попробуй позже").build());
            } finally {
                inFlightSearch.remove(normalized);
            }
        });
    }

    /** Карточка + TXT-отчёт по уже проиндексированному термину. */
    private void presentSearch(long viewerId, long chatId, String term) {
        List<Entry> entries = searchService.search(term);
        boolean isAdmin = viewerId == config.adminId();
        Optional<Term> termEntity = entryService.findTermByQuery(term);
        boolean finalized = termEntity.map(Term::isFinalized).orElse(false);
        Long termId = termEntity.map(Term::getId).orElse(null);
        String summary = termEntity.map(Term::getSummary).orElse(null);

        // Показываем пользователю только отфильтрованное и отсортированное;
        // кнопки модерации — по всем вхождениям (админ видит и то, что фильтр отбросил)
        List<Entry> presentable = entryService.presentable(term);

        InlineKeyboardMarkup keyboard = BotMessageFormatter.buildModerationKeyboard(entries, viewerId,
                config.adminId(), finalized ? null : termId);
        keyboard = withExtraButtons(keyboard, termId);
        SendMessage header = SendMessage.builder()
                .chatId(chatId)
                .text(BotMessageFormatter.buildCard(term, presentable, finalized, summary, buildUsageLines(presentable)))
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

        if (!finalized) {
            scheduleAiExtras(term, termId, chatId);
        }
    }

    /** Разделы использования из оглавлений книг: «• Зонная теория — со стр. 45». */
    private List<String> buildUsageLines(List<Entry> entries) {
        Map<Long, List<TocMapper.TocSection>> tocByBook = new java.util.HashMap<>();
        Map<String, Integer> ordered = new java.util.LinkedHashMap<>();
        for (Entry entry : entries) {
            String toc = entry.getBook().getToc();
            if (toc == null) continue;
            List<TocMapper.TocSection> sections =
                    tocByBook.computeIfAbsent(entry.getBook().getId(), id -> TocMapper.flatten(toc));
            String section = TocMapper.sectionFor(sections, entry.getPageNumber());
            if (section != null) {
                ordered.putIfAbsent(section, entry.getPageNumber());
            }
        }
        return ordered.entrySet().stream()
                .limit(5)
                .map(e -> "• " + e.getKey() + " — со стр. " + e.getValue())
                .toList();
    }

    /** Фоновые LLM-экстры после индексации: классификация вхождений + синтез определения. */
    private void scheduleAiExtras(String term, Long termId, long chatId) {
        if (!summaryService.isEnabled() || termId == null) return;
        if (synthesisInFlight.putIfAbsent(termId, true) != null) return;
        aiExecutor.submit(() -> {
            try {
                summaryService.classifyTerm(term);
                entryService.findTermByQuery(term).ifPresent(t -> {
                    if (t.getSummary() == null || t.getSummary().isBlank()) {
                        summaryService.explain(term).ifPresent(explanation -> {
                            String text = BotMessageFormatter.esc("🧠 Нейро-определение «" + term + "»:\n"
                                    + explanation.text() + "\n\n📚 Источники: " + explanation.sources());
                            executeSilently(SendMessage.builder().chatId(chatId)
                                    .text(text)
                                    .parseMode(ParseMode.HTML)
                                    .build());
                        });
                    }
                });
            } catch (Exception e) {
                log.warn("LLM-экстры для «{}» не удались: {}", term, e.getMessage());
            } finally {
                synthesisInFlight.remove(termId);
            }
        });
    }

    /** PDF-файл в диалоге /upload: скачивание, заявка, уведомление админа. */
    private void handleDocument(Message message) {
        long chatId = message.getChatId();
        Long subjectId = uploadSubjectPick.remove(chatId);
        if (subjectId == null) {
            executeSilently(SendMessage.builder().chatId(chatId)
                    .text("Сначала выбери предмет: /upload").build());
            return;
        }
        long userId = message.getFrom() != null ? message.getFrom().getId() : chatId;

        var document = message.getDocument();
        if (document.getFileSize() != null && document.getFileSize() > MAX_PDF_BYTES) {
            executeSilently(SendMessage.builder().chatId(chatId)
                    .text("Файл больше 20 МБ — Telegram не даёт ботам скачивать такие файлы 🙈").build());
            return;
        }

        executeSilently(SendMessage.builder().chatId(chatId)
                .text("⏳ Проверяю файл… это займёт до минуты").build());
        aiExecutor.submit(() -> {
            try {
                Files.createDirectories(UPLOADS_DIR);
                var getFile = execute(new org.telegram.telegrambots.meta.api.methods.GetFile(document.getFileId()));
                String safeName = document.getFileName() == null ? "book.pdf" : document.getFileName();
                File pdf = downloadFile(getFile, UPLOADS_DIR.resolve(UUID.randomUUID() + "-" + safeName).toFile());

                var outcome = bookService.createRequest(userId, subjectId, pdf.toPath(), safeName);
                executeSilently(SendMessage.builder().chatId(chatId).text(outcome.message()).build());
                if (outcome.ok()) {
                    sendRequestToAdmin(outcome);
                }
            } catch (Exception e) {
                log.error("Ошибка обработки PDF от пользователя {}", userId, e);
                executeSilently(SendMessage.builder().chatId(chatId)
                        .text("⚠️ Не удалось обработать файл, попробуй ещё раз").build());
            }
        });
    }

    private void sendRequestToAdmin(BookService.RequestOutcome outcome) {
        BookRequest request = outcome.request();
        executeSilently(SendMessage.builder()
                .chatId(config.adminId())
                .text("📥 Новая заявка на книгу\n"
                        + "👤 от: " + request.getUserTelegramId() + "\n"
                        + "📚 предмет: " + outcome.subjectName() + "\n"
                        + "📄 файл: " + request.getTitle())
                .replyMarkup(BotMessageFormatter.buildRequestAdminKeyboard(request.getId()))
                .build());
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

        if (data.startsWith("picksub:")) {
            // диалог /upload доступен любому пользователю
            long subjectId = Long.parseLong(data.substring("picksub:".length()));
            if (callbackQuery.getMessage() != null) {
                uploadSubjectPick.put(callbackQuery.getMessage().getChatId(), subjectId);
            }
            answerCallback(callbackQuery.getId(), "Теперь отправь файл PDF (до 20 МБ)");
            return;
        }

        if (data.startsWith("word:")) {
            handleWord(callbackQuery, Long.parseLong(data.substring("word:".length())));
            return;
        }

        if (data.startsWith("vote:")) {
            String[] parts = data.split(":");
            boolean helpful = parts.length > 2 && parts[2].equals("up");
            handleVote(callbackQuery, Long.parseLong(parts[1]), helpful);
            return;
        }

        if (data.startsWith("simpler:") || data.startsWith("stricter:")) {
            handleVariant(callbackQuery, data);
            return;
        }

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

        if (data.startsWith("reqap:")) {
            handleRequestApprove(callbackQuery, Long.parseLong(data.substring("reqap:".length())));
        } else if (data.startsWith("reqre:")) {
            handleRequestReject(callbackQuery, Long.parseLong(data.substring("reqre:".length())));
        } else if (data.startsWith("approve:")) {
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

    /** ⚖️ Очередь модерации: заявки на книги + эскалированные термины. Только админ. */
    private void handlePending(long chatId) {
        if (chatId != config.adminId()) {
            executeSilently(SendMessage.builder().chatId(chatId)
                    .text("⚖️ Очередь модерации доступна только админу").build());
            return;
        }
        var pending = bookService.pendingRequests();
        var escalated = feedbackService.escalatedTerms();
        if (pending.isEmpty() && escalated.isEmpty()) {
            executeSilently(SendMessage.builder().chatId(chatId)
                    .text("🎉 Очередь пуста: заявок на книги и эскалированных терминов нет").build());
            return;
        }
        for (BookRequest request : pending) {
            executeSilently(SendMessage.builder().chatId(chatId)
                    .text("📥 Заявка #" + request.getId()
                            + "\n👤 от: " + request.getUserTelegramId()
                            + "\n📄 файл: " + request.getTitle())
                    .replyMarkup(BotMessageFormatter.buildRequestAdminKeyboard(request.getId()))
                    .build());
        }
        if (!escalated.isEmpty()) {
            StringBuilder sb = new StringBuilder("⚠️ Термины с эскалацией (≥3 👎):\n");
            for (var term : escalated) {
                sb.append("• «").append(term.getDisplayForm())
                        .append("» (👍 ").append(term.getUpvotes())
                        .append(" · 👎 ").append(term.getDownvotes()).append(")\n");
            }
            sb.append("Проверь: отправь термин боту и отмодерируй ✅/🔒.");
            executeSilently(SendMessage.builder().chatId(chatId).text(sb.toString()).build());
        }
    }

    /** Одобрение заявки: книга попадает в базу, извлечение страниц уходит в фон. */
    private void handleRequestApprove(CallbackQuery callbackQuery, long requestId) {
        org.idubinov.termfind.models.Book book;
        try {
            book = bookService.approve(requestId);
        } catch (IllegalStateException e) {
            answerCallback(callbackQuery.getId(), e.getMessage());
            return;
        }
        answerCallback(callbackQuery.getId(), "✅ Одобрено, парсю книгу…");
        if (callbackQuery.getMessage() != null) {
            editMessage(callbackQuery.getMessage().getMessageId(), callbackQuery.getMessage().getChatId(),
                    "⏳ Индексирую «" + book.getTitle() + "»…");
        }
        extractionExecutor.submit(() -> {
            try {
                var stats = bookService.extractBook(book.getId());
                if (callbackQuery.getMessage() != null) {
                    editMessage(callbackQuery.getMessage().getMessageId(), callbackQuery.getMessage().getChatId(),
                            "📖 «" + book.getTitle() + "» готова: " + stats.pages() + " стр., "
                                    + stats.definitions() + " определений, оглавление: "
                                    + (stats.tocFound() ? "есть" : "нет"));
                }
            } catch (Exception e) {
                log.error("Ошибка индексации книги «{}»", book.getTitle(), e);
                if (callbackQuery.getMessage() != null) {
                    editMessage(callbackQuery.getMessage().getMessageId(), callbackQuery.getMessage().getChatId(),
                            "⚠️ Ошибка индексации «" + book.getTitle() + "», см. логи");
                }
            }
        });
    }

    private void handleRequestReject(CallbackQuery callbackQuery, long requestId) {
        try {
            bookService.reject(requestId, "Отклонено админом");
            answerCallback(callbackQuery.getId(), "❌ Отклонено");
            if (callbackQuery.getMessage() != null) {
                editMessage(callbackQuery.getMessage().getMessageId(), callbackQuery.getMessage().getChatId(),
                        "❌ Заявка отклонена");
            }
        } catch (IllegalStateException e) {
            answerCallback(callbackQuery.getId(), e.getMessage());
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

    private InlineKeyboardMarkup withExtraButtons(InlineKeyboardMarkup keyboard, Long termId) {
        if (termId == null) return keyboard;
        List<List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton>> rows =
                new java.util.ArrayList<>(keyboard != null ? keyboard.getKeyboard() : List.of());
        if (summaryService.isEnabled()) {
            rows.add(0, List.of(
                    org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton.builder()
                            .text("🧠 Объяснить").callbackData("explain:" + termId).build(),
                    org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton.builder()
                            .text("💡 Проще").callbackData("simpler:" + termId).build(),
                    org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton.builder()
                            .text("🔬 Строже").callbackData("stricter:" + termId).build()));
        }
        rows.add(List.of(
                org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton.builder()
                        .text("📄 Word").callbackData("word:" + termId).build(),
                org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton.builder()
                        .text("👍 Верное").callbackData("vote:" + termId + ":up").build(),
                org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton.builder()
                        .text("👎 Не то").callbackData("vote:" + termId + ":down").build()));
        return new InlineKeyboardMarkup(rows);
    }

    /** 📄 Word: сборка .docx «как в учебнике» в фоновом пуле, отправка файлом. */
    private void handleWord(CallbackQuery callbackQuery, long termId) {
        long chatId = callbackQuery.getMessage() != null ? callbackQuery.getMessage().getChatId() : 0;
        answerCallback(callbackQuery.getId(), "📄 Собираю Word… до 10 сек");
        aiExecutor.submit(() -> {
            try {
                var termOpt = entryService.findTermById(termId);
                if (termOpt.isEmpty()) return;
                String termName = termOpt.get().getDisplayForm();
                List<Entry> presentable = entryService.presentable(termName);
                byte[] docx = wordService.export(termName, presentable, buildUsageLines(presentable));
                execute(org.telegram.telegrambots.meta.api.methods.send.SendDocument.builder()
                        .chatId(chatId)
                        .document(new InputFile(new java.io.ByteArrayInputStream(docx), WordService.fileName(termName)))
                        .build());
            } catch (Exception e) {
                log.error("Ошибка генерации Word для термина {}", termId, e);
                executeSilently(SendMessage.builder().chatId(chatId)
                        .text("⚠️ Не удалось собрать Word, попробуй позже").build());
            }
        });
    }

    /** 👍/👎: голос, пересчёт, правила верификации/эскалации. */
    private void handleVote(CallbackQuery callbackQuery, long termId, boolean helpful) {
        long userId = callbackQuery.getFrom().getId();
        try {
            var result = feedbackService.vote(userId, termId, helpful);
            answerCallback(callbackQuery.getId(),
                    (helpful ? "👍" : "👎") + " учтено · 👍 " + result.upvotes() + " · 👎 " + result.downvotes()
                            + (result.justVerified() ? " — термин верифицирован ✅" : ""));
            if (result.justEscalated() && callbackQuery.getMessage() != null) {
                entryService.findTermById(termId).ifPresent(term ->
                        executeSilently(SendMessage.builder()
                                .chatId(config.adminId())
                                .text("⚠️ Термин «" + term.getDisplayForm() + "» набрал ≥3 👎 "
                                        + "(👍 " + result.upvotes() + " · 👎 " + result.downvotes() + "). "
                                        + "Проверь: отправь боту «" + term.getDisplayForm() + "»")
                                .build()));
            }
        } catch (java.util.NoSuchElementException e) {
            answerCallback(callbackQuery.getId(), "Термин не найден");
        }
    }

    /** 💡 Проще / 🔬 Строже: LLM-вариант объяснения (кэшируется в llm_cache). */
    private void handleVariant(CallbackQuery callbackQuery, String data) {
        boolean stricter = data.startsWith("stricter:");
        long termId = Long.parseLong(data.substring((stricter ? "stricter:" : "simpler:").length()));
        long chatId = callbackQuery.getMessage() != null ? callbackQuery.getMessage().getChatId() : 0;
        entryService.findTermById(termId).ifPresentOrElse(term -> {
            answerCallback(callbackQuery.getId(), stricter ? "🔬 Формулирую строго… до 20 сек" : "💡 Упрощаю… до 20 сек");
            aiExecutor.submit(() -> {
                try {
                    var variant = summaryService.variant(term.getDisplayForm(), stricter);
                    if (variant.isEmpty()) {
                        executeSilently(SendMessage.builder().chatId(chatId)
                                .text("❌ Недостаточно данных для «" + term.getDisplayForm() + "»").build());
                        return;
                    }
                    String text = (stricter ? "🔬 " : "💡 ") + variant.get().text()
                            + "\n\n📚 Источники: " + variant.get().sources();
                    executeSilently(SendMessage.builder().chatId(chatId)
                            .text(BotMessageFormatter.esc(text)).parseMode(ParseMode.HTML).build());
                } catch (Exception e) {
                    log.error("Ошибка генерации варианта", e);
                    executeSilently(SendMessage.builder().chatId(chatId)
                            .text("⚠️ Нейросеть недоступна, попробуй позже").build());
                }
            });
        }, () -> answerCallback(callbackQuery.getId(), "Термин не найден"));
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
