package com.example.WebChat.UtilsConfigs;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import java.util.List;

@Data
@Validated
@Configuration
@ConfigurationProperties(prefix = "application")
public class AppProperties {

    private final Security security = new Security();
    private final Storage storage = new Storage();
    private final Websocket websocket = new Websocket();

    @Data
    public static class Security {
        @NotBlank
        private String jwtSecret;

        @Min(1000)
        private long jwtExpirationMs;
    }


    @Data
    public static class Websocket {
        private List<String> allowedOrigins = List.of("http://localhost:5173");
    }

    @Data
    public static class Storage {
        @NotBlank
        private String location;

        @Min(1024)
        private long maxFileSize;
    }
}