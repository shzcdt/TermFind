package org.idubinov.termfind.service;

import org.idubinov.termfind.models.Subject;
import org.idubinov.termfind.repositories.BookSubjectRepository;
import org.idubinov.termfind.repositories.SubjectRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SubjectService implements ApplicationRunner {

    /** Базовый набор предметов (FR-6 ТЗ), досевается при каждом старте идемпотентно. */
    private static final List<String> DEFAULT_SUBJECTS = List.of(
            "Механика", "Термодинамика", "Электродинамика", "Оптика",
            "Квантовая механика", "Физика твёрдого тела",
            "Математическая физика", "Теория поля");

    public record SubjectView(String name, String description, long bookCount) {
    }

    private final SubjectRepository subjectRepository;
    private final BookSubjectRepository bookSubjectRepository;

    public SubjectService(SubjectRepository subjectRepository, BookSubjectRepository bookSubjectRepository) {
        this.subjectRepository = subjectRepository;
        this.bookSubjectRepository = bookSubjectRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (String name : DEFAULT_SUBJECTS) {
            if (subjectRepository.findByName(name).isEmpty()) {
                subjectRepository.save(new Subject(name));
            }
        }
    }

    @Transactional(readOnly = true)
    public List<SubjectView> listWithBookCounts() {
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : bookSubjectRepository.countBooksPerSubject()) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return subjectRepository.findAll().stream()
                .sorted(Comparator.comparing(Subject::getName))
                .map(s -> new SubjectView(s.getName(), s.getDescription(),
                        counts.getOrDefault(s.getId(), 0L)))
                .toList();
    }
}
