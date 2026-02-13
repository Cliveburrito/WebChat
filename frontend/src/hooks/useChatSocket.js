import SockJS from "sockjs-client";
import { Client } from "@stomp/stompjs";
import { useEffect, useRef, useState } from "react";

export function useChatSocket({ token, username, onNotification, onPresenceUpdate }) {
    const [stompClient, setStompClient] = useState(null);
    const [onlineUsers, setOnlineUsers] = useState([]);

    const onNotificationRef = useRef(onNotification);
    const onPresenceUpdateRef = useRef(onPresenceUpdate);

    useEffect(() => {
        onNotificationRef.current = onNotification;
        onPresenceUpdateRef.current = onPresenceUpdate;
    }, [onNotification, onPresenceUpdate]);

    useEffect(() => {
        if (!token || !username) return;

        let isCancelled = false;
        let heartbeatInterval = null; // Reference for cleanup
        const socketUrl = "http://localhost:8080/ws";

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
                console.log("Connected to STOMP as", username);

                // --- START HEARTBEAT LOGIC ---
                // Send a pulse every 45 seconds to keep the Redis key alive (TTL 120s)
                heartbeatInterval = setInterval(() => {
                    if (client.connected) {
                        client.publish({ destination: "/app/presence/heartbeat" });
                        console.debug("Heartbeat pulse sent");
                    }
                }, 45000);

                // Subscribe to Personal Notifications
                client.subscribe(`/topic/notifications/${username}`, (frame) => {
                    try {
                        const dto = JSON.parse(frame.body);
                        onNotificationRef.current?.(dto);
                    } catch (e) {
                        console.error("Bad Notification payload:", e);
                    }
                });

                // Subscribe to Global Presence Updates
                client.subscribe(`/topic/public/presence`, (frame) => {
                    try {
                        const onlineList = JSON.parse(frame.body);
                        setOnlineUsers(onlineList);
                        onPresenceUpdateRef.current?.(onlineList);
                    } catch (e) {
                        console.error("Bad Presence payload:", e);
                    }
                });

                // Subscribe to Personal Presence Sync (for initial load)
                client.subscribe(`/user/topic/public/presence`, (frame) => {
                    const onlineList = JSON.parse(frame.body);
                    setOnlineUsers(onlineList);
                    onPresenceUpdateRef.current?.(onlineList);
                });

                // Initial request for the online users list
                client.publish({ destination: "/app/presence/sync" });
            },
            onStompError: (frame) => {
                console.error("STOMP error:", frame.headers["message"]);
            },
            onWebSocketClose: () => {
                console.log("WS Connection Closed");
                setStompClient(null);
                setOnlineUsers([]);
                if (heartbeatInterval) clearInterval(heartbeatInterval);
            }
        });

        client.activate();

        return () => {
            isCancelled = true;
            if (heartbeatInterval) clearInterval(heartbeatInterval);
            if (client) {
                client.deactivate();
                setStompClient(null);
            }
        };
    }, [token, username]);

    return { stompClient, onlineUsers };
}