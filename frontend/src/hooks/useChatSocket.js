import SockJS from "sockjs-client";
import { Client } from "@stomp/stompjs";
import { useEffect, useState } from "react";

export function useChatSocket({ token, username, onPresenceUpdate, debug = false }) {
    const [stompClient, setStompClient] = useState(null);
    const [onlineUsers, setOnlineUsers] = useState([]);

    useEffect(() => {
        if (!token || !username) return;

        let isCancelled = false;
        let heartbeatInterval = null;

        const socketUrl = import.meta?.env?.VITE_WS_URL || "http://localhost:8080/ws";

        const client = new Client({
            webSocketFactory: () => new SockJS(socketUrl),
            reconnectDelay: 5000,
            connectHeaders: { Authorization: `Bearer ${token}` },

            onConnect: () => {
                if (isCancelled) {
                    client.deactivate();
                    return;
                }

                setStompClient(client);
                if (debug) console.log("[WS] connected as", username);

                // Keep presence alive (Redis TTL)
                heartbeatInterval = setInterval(() => {
                    if (client.connected) client.publish({ destination: "/app/presence/heartbeat" });
                }, 45000);

                const handlePresenceFrame = (frame) => {
                    try {
                        const list = JSON.parse(frame.body);
                        setOnlineUsers(list);
                        onPresenceUpdate?.(list);
                    } catch (e) {
                        console.error("[WS] Bad presence payload:", e);
                    }
                };

                // Public broadcast for all users
                client.subscribe("/topic/public/presence", handlePresenceFrame);

                // User-scoped sync reply (convertAndSendToUser on backend)
                client.subscribe("/user/topic/public/presence", handlePresenceFrame);

                // Initial sync request
                client.publish({ destination: "/app/presence/sync" });
            },

            onStompError: (frame) => {
                console.error("[WS] STOMP error:", frame.headers["message"]);
            },

            onWebSocketClose: () => {
                if (debug) console.log("[WS] closed");
                setStompClient(null);
                setOnlineUsers([]);
                if (heartbeatInterval) clearInterval(heartbeatInterval);
            },
        });

        client.activate();

        return () => {
            isCancelled = true;
            if (heartbeatInterval) clearInterval(heartbeatInterval);
            client.deactivate();
            setStompClient(null);
        };
    }, [token, username, debug, onPresenceUpdate]);

    return { stompClient, onlineUsers };
}
