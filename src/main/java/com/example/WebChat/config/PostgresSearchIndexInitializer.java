package com.example.WebChat.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PostgresSearchIndexInitializer implements ApplicationRunner {
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        jdbcTemplate.execute("""
            CREATE INDEX IF NOT EXISTS idx_messages_search_fts
            ON messages
            USING GIN (to_tsvector('simple', coalesce(message, '')))
        """);

        log.info("Ensured PostgreSQL GIN full-text index exists for messages.message");
    }
}
