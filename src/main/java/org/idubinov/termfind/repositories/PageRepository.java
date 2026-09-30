package org.idubinov.termfind.repositories;

import org.idubinov.termfind.models.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PageRepository extends JpaRepository<Page, Long> {

    List<Page> findByBookIdOrderByPageNumber(Long bookId);

    void deleteByBookId(Long bookId);
}
