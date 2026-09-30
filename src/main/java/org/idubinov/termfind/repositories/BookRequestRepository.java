package org.idubinov.termfind.repositories;

import org.idubinov.termfind.models.BookRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BookRequestRepository extends JpaRepository<BookRequest, Long> {

    boolean existsByFileHashAndStatus(String fileHash, BookRequest.Status status);

    List<BookRequest> findByStatusOrderByIdAsc(BookRequest.Status status);
}
