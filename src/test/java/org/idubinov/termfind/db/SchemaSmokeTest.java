package org.idubinov.termfind.db;

import org.hibernate.Session;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Смоук-тест маппинга: схема создается на in-memory H2,
 * полный цикл вставки и чтения всех трех сущностей.
 */
class SchemaSmokeTest {

    private static SessionFactoryHolder holder;

    @BeforeAll
    static void init() {
        holder = new SessionFactoryHolder(
                HibernateUtil.build("jdbc:h2:mem:termfind;DB_CLOSE_DELAY=-1", "sa", ""));
    }

    @AfterAll
    static void close() {
        holder.close();
    }

    @Test
    void fullCycle() {
        Session session = holder.sessionFactory().openSession();
        session.beginTransaction();

        Book book = new Book("Наноструктуры", "books/glinskii.pdf", 324);
        session.persist(book);

        Term term = new Term("тензором ранга N", "тенз ранг");
        session.persist(term);

        Entry entry = new Entry(term, book, 14,
                "тензором ранга N называется величина...", Entry.EntryType.DEFINITION, false);
        session.persist(entry);

        session.getTransaction().commit();

        Entry loaded = session.createQuery(
                        "from Entry e join fetch e.term join fetch e.book where e.term.normalizedForm = :nf",
                        Entry.class)
                .setParameter("nf", "тенз ранг")
                .uniqueResult();

        assertNotNull(loaded);
        assertEquals(14, loaded.getPageNumber());
        assertEquals("Наноструктуры", loaded.getBook().getTitle());
        assertEquals(Entry.EntryType.DEFINITION, loaded.getType());
        assertFalse(loaded.isApproved());
        session.close();
    }

    /** Мелкая обертка, чтобы держать фабрику до конца теста и закрыть в @AfterAll. */
    record SessionFactoryHolder(org.hibernate.SessionFactory sessionFactory) implements AutoCloseable {
        @Override
        public void close() {
            sessionFactory.close();
        }
    }
}
