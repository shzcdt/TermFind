package org.idubinov.termfind.service;

import org.idubinov.termfind.models.Feedback;
import org.idubinov.termfind.models.Term;
import org.idubinov.termfind.repositories.FeedbackRepository;
import org.idubinov.termfind.repositories.TermRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Крауд-верификация: 👍/👎 пользователей по терминам.
 * Правила: net >= 3 👍 → термин верифицирован (Level-1 мгновенный ответ);
 * net <= 3 👎 → эскалация админу (однократно, при переходе порога).
 */
@Service
public class FeedbackService {

    /** Порог для auto-verified / эскалации. */
    public static final int VERIFIED_THRESHOLD = 3;

    public record VoteResult(String action, int upvotes, int downvotes,
                             boolean justVerified, boolean justEscalated) {
    }

    private final FeedbackRepository feedbackRepository;
    private final TermRepository termRepository;

    public FeedbackService(FeedbackRepository feedbackRepository, TermRepository termRepository) {
        this.feedbackRepository = feedbackRepository;
        this.termRepository = termRepository;
    }

    /**
     * Голос пользователя. Повтор того же голоса — отмена (toggle), смена — переголосование.
     */
    @Transactional
    public VoteResult vote(long userTelegramId, long termId, boolean helpful) {
        Term term = termRepository.findById(termId)
                .orElseThrow(() -> new NoSuchElementException("Термин не найден"));
        int oldNet = term.getUpvotes() - term.getDownvotes();

        String action;
        Optional<Feedback> existing = feedbackRepository.findByUserTelegramIdAndTermId(userTelegramId, termId);
        if (existing.isPresent() && existing.get().isHelpful() == helpful) {
            feedbackRepository.delete(existing.get());
            action = "CANCELLED";
        } else if (existing.isPresent()) {
            existing.get().setHelpful(helpful);
            action = "CHANGED";
        } else {
            feedbackRepository.save(new Feedback(userTelegramId, term, helpful));
            action = "ADDED";
        }

        int up = feedbackRepository.countByTermIdAndHelpful(termId, true);
        int down = feedbackRepository.countByTermIdAndHelpful(termId, false);
        term.setUpvotes(up);
        term.setDownvotes(down);

        int net = up - down;
        boolean justVerified = false;
        if (net >= VERIFIED_THRESHOLD && !term.isVerified()) {
            term.setVerified(true);
            justVerified = true;
        }
        boolean justEscalated = net <= -VERIFIED_THRESHOLD && oldNet > -VERIFIED_THRESHOLD;

        return new VoteResult(action, up, down, justVerified, justEscalated);
    }

    /** Термины, дождавшиеся эскалации: на проверку админу в /pending. */
    @Transactional(readOnly = true)
    public List<Term> escalatedTerms() {
        return termRepository.findEscalated();
    }
}
