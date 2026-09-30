package org.idubinov.termfind.repositories;

import org.idubinov.termfind.models.AnswerFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AnswerFeedbackRepository extends JpaRepository<AnswerFeedback, Long> {

    Optional<AnswerFeedback> findByUserTelegramIdAndTermIdAndKind(
            long userTelegramId, Long termId, AnswerFeedback.Kind kind);

    int countByTermIdAndKindAndHelpful(Long termId, AnswerFeedback.Kind kind, boolean helpful);
}
