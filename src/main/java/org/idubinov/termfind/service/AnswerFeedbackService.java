package org.idubinov.termfind.service;

import org.idubinov.termfind.models.AnswerFeedback;
import org.idubinov.termfind.models.Term;
import org.idubinov.termfind.repositories.AnswerFeedbackRepository;
import org.idubinov.termfind.repositories.TermRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Оценки LLM-объяснений (🧠/💡/🔬): копим качество промптов по типам ответов.
 * Логика голоса как у термов: повтор — отмена, смена — переголосование.
 */
@Service
public class AnswerFeedbackService {

    public record VoteResult(String action, int upvotes, int downvotes) {
    }

    private final AnswerFeedbackRepository repository;
    private final TermRepository termRepository;

    public AnswerFeedbackService(AnswerFeedbackRepository repository, TermRepository termRepository) {
        this.repository = repository;
        this.termRepository = termRepository;
    }

    @Transactional
    public VoteResult vote(long userTelegramId, long termId, AnswerFeedback.Kind kind, boolean helpful) {
        Term term = termRepository.findById(termId)
                .orElseThrow(() -> new NoSuchElementException("Термин не найден"));

        String action;
        Optional<AnswerFeedback> existing =
                repository.findByUserTelegramIdAndTermIdAndKind(userTelegramId, termId, kind);
        if (existing.isPresent() && existing.get().isHelpful() == helpful) {
            repository.delete(existing.get());
            action = "CANCELLED";
        } else if (existing.isPresent()) {
            existing.get().setHelpful(helpful);
            action = "CHANGED";
        } else {
            repository.save(new AnswerFeedback(userTelegramId, term, kind, helpful));
            action = "ADDED";
        }

        return new VoteResult(action,
                repository.countByTermIdAndKindAndHelpful(termId, kind, true),
                repository.countByTermIdAndKindAndHelpful(termId, kind, false));
    }
}
