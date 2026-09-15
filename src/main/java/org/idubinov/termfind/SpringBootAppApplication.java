package org.idubinov.termfind;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@ComponentScan("org.idubinov.termfind")
@EnableJpaRepositories("org.idubinov.termfind.repositories")
@EntityScan("org.idubinov.termfind.models")
public class SpringBootAppApplication {
    public static void main(String[] args) {
        SpringApplication.run(SpringBootApplication.class, args);
    }
}