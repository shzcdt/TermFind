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
}

tasks.test {
    useJUnitPlatform()
}