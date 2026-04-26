package com.herbscript;

import com.herbscript.recognition.config.RecognitionProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(RecognitionProperties.class)
public class HerbScriptApplication {

    public static void main(String[] args) {
        SpringApplication.run(HerbScriptApplication.class, args);
    }
}
