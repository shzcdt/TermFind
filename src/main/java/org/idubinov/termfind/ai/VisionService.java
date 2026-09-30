package org.idubinov.termfind.ai;

import org.idubinov.termfind.models.BookImage;
import org.idubinov.termfind.models.Entry;
import org.idubinov.termfind.repositories.BookImageRepository;
import org.idubinov.termfind.util.PdfPageRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Vision: описания схем/рисунков на страницах с определениями термина.
 * Источник картинки — рендер страницы (надёжнее извлечения встроенных картинок).
 * Описание кэшируется в images.description (привязано к странице, не к запросу).
 */
@Service
public class VisionService {

    private static final Logger log = LoggerFactory.getLogger(VisionService.class);
    /** Mitigation из ТЗ: не даём Word распухать. */
    private static final int MAX_SCHEMAS = 2;
    private static final Path IMAGES_DIR = Path.of("images");

    private static final String VISION_PROMPT = """
            Ты — научный консультант для студентов. Тебе дают отрендеренную страницу учебника.
            Правила:
            1. Опиши, что изображено (схема, график, рисунок) и как это связано с указанным термином.
            2. Если на странице нет иллюстраций — коротко скажи, что это за страница (раздел текста).
            3. 3-5 предложений, по-русски. Не выдумывай, чего не видно на странице.""";

    public record Schema(Long bookId, String bookTitle, int page, String description) {
    }

    private final LlmClient llmClient;
    private final BookImageRepository imageRepository;
    private final PdfPageRenderer pageRenderer = new PdfPageRenderer();

    public VisionService(LlmClient llmClient, BookImageRepository imageRepository) {
        this.llmClient = llmClient;
        this.imageRepository = imageRepository;
    }

    public boolean isEnabled() {
        return llmClient.isVisionEnabled();
    }

    /** Описания для топ-страниц с определениями; кэш в images.description. */
    @Transactional
    public List<Schema> describeForTerm(String termName, List<Entry> presentable) {
        if (!isEnabled()) return List.of();

        List<Schema> result = new ArrayList<>();
        Set<String> seenPages = new LinkedHashSet<>();
        for (Entry entry : presentable) {
            if (result.size() >= MAX_SCHEMAS) break;
            if (entry.getType() != Entry.EntryType.DEFINITION) continue;

            var book = entry.getBook();
            int page = entry.getPageNumber();
            if (!seenPages.add(book.getId() + ":" + page)) continue;

            try {
                Optional<BookImage> existing = imageRepository.findByBookIdAndPageNumber(book.getId(), page);
                if (existing.isPresent() && existing.get().getDescription() != null
                        && !existing.get().getDescription().isBlank()) {
                    result.add(new Schema(book.getId(), book.getTitle(), page, existing.get().getDescription()));
                    continue;
                }

                byte[] png = pageRenderer.renderPage(new File(book.getPdfPath()), page);
                String description = llmClient.completeWithImage(VISION_PROMPT,
                        "Термин: «" + termName + "». Страница из книги «" + book.getTitle() + "».", png);

                Files.createDirectories(IMAGES_DIR);
                Path imagePath = IMAGES_DIR.resolve(book.getId() + "_" + page + ".png");
                Files.write(imagePath, png);

                BookImage image = existing.orElseGet(() ->
                        new BookImage(book, page, imagePath.toString(), "PAGE_RENDER"));
                image.setDescription(description);
                imageRepository.save(image);

                result.add(new Schema(book.getId(), book.getTitle(), page, description));
            } catch (Exception e) {
                log.warn("Vision для «{}» стр. {} не удался: {}", book.getTitle(), page, e.getMessage());
            }
        }
        return result;
    }
}
