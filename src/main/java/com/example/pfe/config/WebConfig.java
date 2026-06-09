package com.example.pfe.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.upload.dir:uploads/avatars}")
    private String uploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Résout en chemin absolu, indépendant du working directory
        // uploadDir = "uploads/avatars" → absolute = "/app/uploads/avatars"
        // On veut servir le parent (/app/uploads) sous l'URL /uploads/**
        String absolutePath = Paths.get(uploadDir).toAbsolutePath().getParent().toString();

        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + absolutePath + "/");

        // Log pour debug — tu verras au démarrage où Spring cherche les fichiers
        System.out.println("📁 Static resources: /uploads/** → file:" + absolutePath + "/");
    }
}