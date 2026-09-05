package org.idubinov.termfind;

import org.idubinov.termfind.pdf.ContextExtractor;
import org.idubinov.termfind.pdf.DefinitionDetector;
import org.idubinov.termfind.pdf.PdfTextExtractor;

import java.io.File;

public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Использование: main <путь к pdf>");
            System.err.println("Пример: main books/textbook.pdf термодинамика");
            System.exit(1);
        }


        String searchTerm = args.length >= 2 ? args[1].toLowerCase() : null;


        PdfTextExtractor extractor = new PdfTextExtractor();
        DefinitionDetector detector = new DefinitionDetector();
        ContextExtractor contextExtractor = new ContextExtractor();

        PdfTextExtractor.BookText book = extractor.extract(new File(args[0]));
        System.out.println("Всего страниц: " + book.totalPages());

        for (var page : book.pages()){

            var definitions = detector.detectForTerm(page.text(), page.pageNumber(), searchTerm);
            for(var def : definitions){
                System.out.println("🔹 ОПРЕДЕЛЕНИЕ [стр. " + def.pageNumber() + "]: " + def.definition());
            }

            var mentions = contextExtractor.findMentions(page.text(), page.pageNumber(), searchTerm);
            for (var mention : mentions) {
                System.out.println("📖 Упоминание [стр. " + mention.pageNumber() + "]: " + mention.context());
            }
        }
    }
}