# WebChat — Real-Time Chat Platform with WebSockets & JWT Authentication

WebChat is a full-stack real-time messaging application built with Spring Boot, WebSockets (STOMP/SockJS) and React.
The goal of the project is to simulate a modern chat environment similar to Messenger/WhatsApp — supporting authentication, live messaging, conversations, persistence, and extensible architecture for future features.

## Features

JWT Authentication: Stateless security with JSON Web Tokens.	 <br />
Real-time messaging using WebSocket STOMP	 <br />
Intelligent Rate Limiting: Powered by Bucket4j to prevent brute-force logins and message spamming.  <br />
Message saving & conversation persistence	<br />
Pagination of chat history <br />
Direct & Group conversations<br />
Live Notifications: Real-time unread count updates and push notifications for users. <br />
Simple Web UI (React + SockJS)<br />	
Global Error Handling: Custom exceptions for a smooth frontend experience:<br />

## Tech Stack
### Backend
Java 21 with Spring Boot 3.4.0 <br />
Spring Security (JWT Implementation) <br />
Spring Data JPA (Hibernate) <br />
WebSocket & STOMP <br />
Spring Boot Actuator: For real-time health checks and metric gathering <br />
Micrometer: To bridge Actuator metrics with external monitoring systems <br />
Bucket4j (Rate limiting) <br />
Lombok (Boilerplate reduction) <br />

### Frontend
React (Vite) <br />
SockJS & Stomp.js (WebSocket clients) <br />
Tailwind CSS (Styling) <br />

## Future improvements 
Typing indicator (“User is typing…”)<br />
Online indicator (Like Messenger, a green dot)<br />
Message status (Sent, Deliver, Read) <br />
Searching inside the chat <br />
File/image sharing inside chat<br />
Maybe add calls <br />
Prometheus & Grafana dashboard for visualizing message throughput and rate-limit triggers. <br />
