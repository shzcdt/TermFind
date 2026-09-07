plugins {
    id("java")
    id("application")
}

application {
    mainClass.set("org.idubinov.termfind.Main")
}

group = "org.idubinov.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    implementation("org.apache.lucene:lucene-core:9.12.3")
    implementation("org.apache.pdfbox:pdfbox:3.0.8")
    implementation("com.github.rholder:snowball-stemmer:1.3.0.581.1")

    // БД: Hibernate (ORM/JPA) + драйвер PostgreSQL
    implementation("org.hibernate.orm:hibernate-core:6.6.4.Final")
    implementation("org.hibernate.orm:hibernate-community-dialects:6.6.4.Final")
    implementation("org.postgresql:postgresql:42.7.5")

    // H2 — только для тестов: проверка маппинга без запущенного PostgreSQL
    testImplementation("com.h2database:h2:2.3.232")
}

// Параметры подключения к БД можно переопределить переменными окружения
tasks.test {
    useJUnitPlatform()
    environment("DB_URL", "jdbc:h2:mem:termfind;DB_CLOSE_DELAY=-1")
    environment("DB_USER", "sa")
    environment("DB_PASSWORD", "")
}