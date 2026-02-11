import SockJS from "sockjs-client";
import { Client } from "@stomp/stompjs";
import { useEffect, useRef, useState } from "react";

export function useChatSocket({ token, username, onNotification, onPresenceUpdate }) {
    const [stompClient, setStompClient] = useState(null);
    const [onlineUsers, setOnlineUsers] = useState([]); // Local state for presence

    // Use Refs to keep callbacks current without triggering useEffect loops
    const onNotificationRef = useRef(onNotification);
    const onPresenceUpdateRef = useRef(onPresenceUpdate);

    useEffect(() => {
        onNotificationRef.current = onNotification;
        onPresenceUpdateRef.current = onPresenceUpdate;
    }, [onNotification, onPresenceUpdate]);

    useEffect(() => {
        if (!token || !username) return;

        let isCancelled = false;
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

                // 1. Subscribe to Personal Notifications
                client.subscribe(`/topic/notifications/${username}`, (frame) => {
                    try {
                        const dto = JSON.parse(frame.body);
                        onNotificationRef.current?.(dto);
                    } catch (e) {
                        console.error("Bad Notification payload:", e);
                    }
                });

                // 2. Subscribe to Global Presence
                client.subscribe(`/topic/public/presence`, (frame) => {
                    try {
                        const onlineList = JSON.parse(frame.body);
                        setOnlineUsers(onlineList); // Update local state
                        onPresenceUpdateRef.current?.(onlineList); // Call parent if needed
                    } catch (e) {
                        console.error("Bad Presence payload:", e);
                    }
                });

                client.subscribe(`/user/topic/public/presence`, (frame) => {
                    const onlineList = JSON.parse(frame.body);
                    console.log("Sync Presence Received:", onlineList);
                    setOnlineUsers(onlineList);
                    onPresenceUpdateRef.current?.(onlineList);
                });

                client.publish({ destination: "/app/presence/sync" });
            },
            onStompError: (frame) => {
                console.error("STOMP error:", frame.headers["message"]);
            },
            onWebSocketClose: () => {
                console.log("WS Connection Closed");
                setStompClient(null);
                setOnlineUsers([]);
            }
        });

        client.activate();

        return () => {
            isCancelled = true;
            if (client) {
                client.deactivate();
                setStompClient(null);
            }
        };
    }, [token, username]);

    // Return BOTH the client and the list of online users
    return { stompClient, onlineUsers };
}