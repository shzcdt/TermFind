package org.idubinov.termfind.service;

import org.idubinov.termfind.models.Feedback;
import org.idubinov.termfind.models.Term;
import org.idubinov.termfind.repositories.FeedbackRepository;
import org.idubinov.termfind.repositories.TermRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Правила крауд-верификации: добавление/смена/отмена голоса,
 * 3👍 → verified, 3👎 → эскалация (однократно при переходе порога).
 */
class FeedbackServiceTest {

    private FeedbackRepository feedbackRepository;
    private TermRepository termRepository;
    private FeedbackService service;
    private Term term;

    @BeforeEach
    void setUp() {
        feedbackRepository = Mockito.mock(FeedbackRepository.class);
        termRepository = Mockito.mock(TermRepository.class);
        service = new FeedbackService(feedbackRepository, termRepository);
        term = new Term("тензор", "тенз");
        when(termRepository.findById(1L)).thenReturn(Optional.of(term));
        when(feedbackRepository.findByUserTelegramIdAndTermId(anyLong(), eq(1L))).thenReturn(Optional.empty());
        when(feedbackRepository.countByTermIdAndHelpful(eq(1L), eq(true))).thenReturn(1);
        when(feedbackRepository.countByTermIdAndHelpful(eq(1L), eq(false))).thenReturn(0);
    }

    @Test
    void firstVoteIsAddedWithCounts() {
        var result = service.vote(42L, 1L, true);

        assertEquals("ADDED", result.action());
        assertEquals(1, result.upvotes());
        assertEquals(0, result.downvotes());
        assertFalse(result.justVerified());
        verify(feedbackRepository).save(any(Feedback.class));
    }

    @Test
    void sameVoteAgainCancels() {
        when(feedbackRepository.findByUserTelegramIdAndTermId(42L, 1L))
                .thenReturn(Optional.of(new Feedback(42L, term, true)));

        var result = service.vote(42L, 1L, true);

        assertEquals("CANCELLED", result.action());
        verify(feedbackRepository).delete(any(Feedback.class));
    }

    @Test
    void oppositeVoteChanges() {
        when(feedbackRepository.findByUserTelegramIdAndTermId(42L, 1L))
                .thenReturn(Optional.of(new Feedback(42L, term, true)));
        when(feedbackRepository.countByTermIdAndHelpful(eq(1L), eq(false))).thenReturn(1);
        when(feedbackRepository.countByTermIdAndHelpful(eq(1L), eq(true))).thenReturn(0);

        var result = service.vote(42L, 1L, false);

        assertEquals("CHANGED", result.action());
        verify(feedbackRepository, never()).delete(any());
    }

    @Test
    void netThreeUpvotesVerifyTerm_once() {
        when(feedbackRepository.countByTermIdAndHelpful(eq(1L), eq(true))).thenReturn(3);

        var first = service.vote(1L, 1L, true);
        assertTrue(first.justVerified());
        assertTrue(term.isVerified());

        var second = service.vote(2L, 1L, true); // ещё 👍 после верификации
        assertFalse(second.justVerified(), "флаг только при переходе порога");
        assertTrue(term.isVerified());
    }

    @Test
    void netThreeDownvotesEscalate_once() {
        when(feedbackRepository.countByTermIdAndHelpful(eq(1L), eq(false))).thenReturn(3);
        when(feedbackRepository.countByTermIdAndHelpful(eq(1L), eq(true))).thenReturn(0);

        var first = service.vote(1L, 1L, false);
        assertTrue(first.justEscalated());
        assertFalse(term.isVerified());

        // порог уже пройден (oldNet <= -3) — повторной эскалации нет
        when(feedbackRepository.findByUserTelegramIdAndTermId(2L, 1L))
                .thenReturn(Optional.of(new Feedback(2L, term, false)));
        var second = service.vote(2L, 1L, false);
        assertFalse(second.justEscalated());
    }
}
