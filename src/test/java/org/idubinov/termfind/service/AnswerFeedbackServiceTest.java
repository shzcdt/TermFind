package org.idubinov.termfind.service;

import org.idubinov.termfind.models.AnswerFeedback;
import org.idubinov.termfind.models.Term;
import org.idubinov.termfind.repositories.AnswerFeedbackRepository;
import org.idubinov.termfind.repositories.TermRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Голосование за LLM-ответы: add/change/cancel + счётчики.
 */
class AnswerFeedbackServiceTest {

    private AnswerFeedbackRepository repository;
    private AnswerFeedbackService service;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(AnswerFeedbackRepository.class);
        TermRepository termRepository = Mockito.mock(TermRepository.class);
        when(termRepository.findById(1L)).thenReturn(Optional.of(new Term("тензор", "тенз")));
        service = new AnswerFeedbackService(repository, termRepository);

        when(repository.findByUserTelegramIdAndTermIdAndKind(anyLong(), eq(1L), any()))
                .thenReturn(Optional.empty());
        when(repository.countByTermIdAndKindAndHelpful(eq(1L), any(), eq(true))).thenReturn(1);
        when(repository.countByTermIdAndKindAndHelpful(eq(1L), any(), eq(false))).thenReturn(0);
    }

    @Test
    void firstVoteIsAdded() {
        var result = service.vote(42L, 1L, AnswerFeedback.Kind.STRICTER, true);
        assertEquals("ADDED", result.action());
        assertEquals(1, result.upvotes());
        verify(repository).save(any(AnswerFeedback.class));
    }

    @Test
    void sameVoteCancels() {
        when(repository.findByUserTelegramIdAndTermIdAndKind(42L, 1L, AnswerFeedback.Kind.SIMPLER))
                .thenReturn(Optional.of(new AnswerFeedback(42L, new Term("тензор", "тенз"),
                        AnswerFeedback.Kind.SIMPLER, true)));

        var result = service.vote(42L, 1L, AnswerFeedback.Kind.SIMPLER, true);
        assertEquals("CANCELLED", result.action());
        verify(repository).delete(any(AnswerFeedback.class));
    }

    @Test
    void kindsAreIndependent() {
        // голос за SIMPLER не мешает голосу за EXPLAIN — разные строки
        service.vote(42L, 1L, AnswerFeedback.Kind.SIMPLER, true);
        service.vote(42L, 1L, AnswerFeedback.Kind.EXPLAIN, false);
        verify(repository, times(2)).save(any(AnswerFeedback.class));
    }
}
