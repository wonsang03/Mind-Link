package com.mindlink.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class AppConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public WebMvcConfigurer uploadResourceHandler(
            @Value("${app.upload.dir:uploads}") String uploadDir) {
        return new WebMvcConfigurer() {
            @Override
            public void addResourceHandlers(ResourceHandlerRegistry registry) {
                Path path = Paths.get(System.getProperty("user.dir"), uploadDir)
                        .toAbsolutePath().normalize();
                String location = path.toUri().toString();
                if (!location.endsWith("/")) {
                    location += "/";
                }
                registry.addResourceHandler("/uploads/**")
                        .addResourceLocations(location);
            }
        };
    }
}
