package org.idubinov.termfind.repositories;

import org.idubinov.termfind.models.Book;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BookRepository extends JpaRepository<Book, Long> {

    Optional<Book> findByFileHash(String fileHash);
}
