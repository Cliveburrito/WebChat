package com.example.WebChat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class WebChat {

	public static void main(String[] args) {
        SpringApplication.run(WebChat.class, args);
	}
}
