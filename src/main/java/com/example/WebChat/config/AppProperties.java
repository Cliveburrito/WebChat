package com.example.WebChat.config;

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
    private final RabbitMQ rabbitmq = new RabbitMQ();
    private final Tracking tracking = new Tracking();
    private final Watermarks watermarks = new Watermarks();

    @Data
    public static class Security {
        @NotBlank
        private String jwtSecret;

        @Min(1000)
        private long jwtExpirationMs;

        @Min(1000)
        private long refreshExpirationMs = 2592000000L;

        private boolean refreshCookieSecure = false;

        private String refreshCookieSameSite = "Strict";
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

        private List<String> allowedContentTypes = List.of(
                "image/jpeg",
                "image/png",
                "image/webp",
                "application/pdf",
                "video/mp4"
        );

        @Min(64)
        private int previewMaxDimension = 480;
    }

    @Data
    public static class RabbitMQ {
        private final Stomp stomp = new Stomp();

        @Data
        public static class Stomp {
            private String host = "localhost";
            private int port = 61613;
            private String username = "guest";
            private String password = "guest";
        }
    }

    @Data
    public static class Tracking {
        private boolean detailedEnabled = true;

        @Min(1)
        private long slowRequestThresholdMs = 2000;
    }

    @Data
    public static class Watermarks {
        private boolean writeBehindEnabled = true;

        @Min(1)
        private int flushBatchSize = 500;

        @Min(1)
        private long flushFixedDelayMs = 5000;

        @Min(1)
        private int deliveredReceiptMemberLimit = 50;
    }
}
