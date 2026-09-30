package org.idubinov.termfind.repositories;

import org.idubinov.termfind.models.Feedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FeedbackRepository extends JpaRepository<Feedback, Long> {

    Optional<Feedback> findByUserTelegramIdAndTermId(long userTelegramId, Long termId);

    int countByTermIdAndIsHelpful(Long termId, boolean helpful);
}
