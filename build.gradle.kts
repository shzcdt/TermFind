plugins {
    id("java")
    id("application")
    id("org.springframework.boot") version "4.1.0"
    id("io.spring.dependency-management") version "1.1.4"
}

application {
    mainClass.set("org.idubinov.termfind.SpringBootAppApplication")
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

    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    implementation("org.postgresql:postgresql:42.7.13")

    // Telegram Bot API (long polling), без spring-стартера — Boot 4 несовместим с ним
    implementation("org.telegram:telegrambots:6.9.0")

    testImplementation("com.h2database:h2")
}

tasks.test {
    useJUnitPlatform()
}