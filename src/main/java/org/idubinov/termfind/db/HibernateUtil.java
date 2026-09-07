package org.idubinov.termfind.db;

import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;

import java.util.List;

/**
 * Инициализация Hibernate. Параметры подключения берутся из переменных окружения:
 * DB_URL (по умолчанию локальный PostgreSQL), DB_USER, DB_PASSWORD.
 */
public class HibernateUtil {

    private static final List<Class<?>> ENTITIES = List.of(Book.class, Term.class, Entry.class);

    private static volatile SessionFactory sessionFactory;

    public static SessionFactory getSessionFactory() {
        if (sessionFactory == null) {
            synchronized (HibernateUtil.class) {
                if (sessionFactory == null) {
                    sessionFactory = build(
                            System.getenv().getOrDefault("DB_URL",
                                    "jdbc:postgresql://localhost:5432/termfind"),
                            System.getenv().getOrDefault("DB_USER", "postgres"),
                            System.getenv().getOrDefault("DB_PASSWORD", "postgres"));
                }
            }
        }
        return sessionFactory;
    }

    public static SessionFactory build(String url, String user, String password) {
        Configuration cfg = new Configuration()
                .setProperty("hibernate.connection.url", url)
                .setProperty("hibernate.connection.username", user)
                .setProperty("hibernate.connection.password", password)
                .setProperty("hibernate.connection.driver_class", driverFor(url))
                .setProperty("hibernate.dialect", dialectFor(url))
                .setProperty("hibernate.hbm2ddl.auto", "update")
                .setProperty("hibernate.show_sql", "false");

        ENTITIES.forEach(cfg::addAnnotatedClass);
        return cfg.buildSessionFactory();
    }

    private static String driverFor(String url) {
        return url.startsWith("jdbc:h2") ? "org.h2.Driver" : "org.postgresql.Driver";
    }

    private static String dialectFor(String url) {
        return url.startsWith("jdbc:h2")
                ? "org.hibernate.dialect.H2Dialect"
                : "org.hibernate.dialect.PostgreSQLDialect";
    }

    public static void shutdown() {
        if (sessionFactory != null) {
            sessionFactory.close();
            sessionFactory = null;
        }
    }
}
