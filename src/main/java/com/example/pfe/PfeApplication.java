package com.example.pfe;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import io.github.cdimascio.dotenv.Dotenv;
@SpringBootApplication
@EnableScheduling
@EnableAsync
public class PfeApplication {

    public static void main(String[] args) {
        // ⚠️ CHARGE LE .env AVANT Spring (CRITIQUE)
        Dotenv dotenv = Dotenv.configure()
                .ignoreIfMissing()
                .load();

        dotenv.entries().forEach(entry ->
                System.setProperty(entry.getKey(), entry.getValue())
        );

        // Maintenant Spring peut résoudre ${JWT_SECRET}
        SpringApplication.run(PfeApplication.class, args);
    }
}
