package com.herbscript.file;

import com.herbscript.recognition.config.RecognitionProperties;
import java.nio.file.Path;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class UploadResourceConfig implements WebMvcConfigurer {

    private final RecognitionProperties properties;

    public UploadResourceConfig(RecognitionProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path uploadDir = Path.of(properties.getUploadDir()).toAbsolutePath().normalize();
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(uploadDir.getParent().toUri().toString());
    }
}
