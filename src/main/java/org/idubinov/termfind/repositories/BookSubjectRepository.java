package org.idubinov.termfind.repositories;

import org.idubinov.termfind.models.BookSubject;
import org.idubinov.termfind.models.BookSubjectId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BookSubjectRepository extends JpaRepository<BookSubject, BookSubjectId> {

    /** Число книг по каждому предмету одним запросом: [subjectId, count]. */
    @Query("select bs.subject.id, count(bs) from BookSubject bs group by bs.subject.id")
    List<Object[]> countBooksPerSubject();
}
