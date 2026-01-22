import SockJS from "sockjs-client";
import { Client } from "@stomp/stompjs";
import { useEffect, useRef } from "react";

/**
 * Connects to /ws and subscribes to /topic/notifications/{username}
 * Requires connectHeaders: Authorization Bearer token (as your backend expects)
 */
export function useChatSocket({ token, username, onNotification }) {
    const stompRef = useRef(null);

    useEffect(() => {
        if (!token || !username || !onNotification) return;

        if (stompRef.current) {
            try { stompRef.current.deactivate(); } catch {}
            stompRef.current = null;
        }

        const socket = new SockJS("http://localhost:8080/ws");

        const client = new Client({
            webSocketFactory: () => socket,
            reconnectDelay: 3000,
            connectHeaders: { Authorization: `Bearer ${token}` },
            onConnect: () => {
                client.subscribe(`/topic/notifications/${username}`, (frame) => {
                    try {
                        const dto = JSON.parse(frame.body);
                        onNotification(dto);
                    } catch (e) {
                        console.error("Bad WS payload:", e);
                    }
                });
            },
            onStompError: (frame) => {
                console.error("STOMP error:", frame.headers["message"], frame.body);
            },
        });

        client.activate();
        stompRef.current = client;

        return () => {
            try { client.deactivate(); } catch {}
            stompRef.current = null;
        };
    }, [token, username, onNotification]);
}
