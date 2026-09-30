package org.idubinov.termfind.repositories;

import org.idubinov.termfind.models.BookImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BookImageRepository extends JpaRepository<BookImage, Long> {

    Optional<BookImage> findByBookIdAndPageNumber(Long bookId, int pageNumber);
}
